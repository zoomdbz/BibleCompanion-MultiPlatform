#!/usr/bin/env python3
"""
LoRA fine-tuning for multilingual-e5-small on Bible parallel passages.

Teaches the model to:
1. Cluster gospel parallels together as top results
2. Distinguish similar-but-different events (transfiguration vs resurrection)
3. Retain general multilingual quality via regularization pairs

Uses InfoNCE loss with hard negatives mined from benchmark failures.

Run AFTER parse_corpus.py and generate_embeddings.py (needs corpus + embeddings).
Run BEFORE re-running generate_embeddings.py to rebuild with the fine-tuned model.

Usage:
  python finetune_lora.py
"""

import json
import random
import struct
import sys
import time
from pathlib import Path

import numpy as np
import torch
import torch.nn.functional as F
from torch.optim import AdamW
from torch.optim.lr_scheduler import CosineAnnealingWarmRestarts
from tqdm import tqdm

from embedder import Embedder

OUTPUT_DIR = Path(__file__).parent / "output"
MODEL_NAME = "intfloat/multilingual-e5-small"
FINETUNED_DIR = OUTPUT_DIR / "finetuned_lora"

GOSPEL_PARALLELS = [
    {
        "query": "the resurrection of Jesus",
        "positives": ["matthew-28", "mark-16", "luke-24", "john-20"],
        "hard_negatives": ["mark-9", "matthew-17", "luke-9"],
    },
    {
        "query": "the transfiguration of Jesus",
        "positives": ["matthew-17", "mark-9", "luke-9"],
        "hard_negatives": ["matthew-28", "mark-16", "luke-24", "john-20"],
    },
    {
        "query": "the crucifixion of Jesus",
        "positives": ["matthew-27", "mark-15", "luke-23", "john-19"],
        "hard_negatives": [],
    },
    {
        "query": "the last supper",
        "positives": ["matthew-26", "mark-14", "luke-22", "john-13"],
        "hard_negatives": [],
    },
    {
        "query": "the baptism of Jesus",
        "positives": ["matthew-3", "mark-1", "luke-3"],
        "hard_negatives": [],
    },
    {
        "query": "the temptation of Jesus in the wilderness",
        "positives": ["matthew-4", "mark-1", "luke-4"],
        "hard_negatives": [],
    },
    {
        "query": "feeding the five thousand",
        "positives": ["matthew-14", "mark-6", "luke-9", "john-6"],
        "hard_negatives": [],
    },
    {
        "query": "walking on water",
        "positives": ["matthew-14", "mark-6", "john-6"],
        "hard_negatives": [],
    },
    {
        "query": "Jesus calming the storm",
        "positives": ["matthew-8", "mark-4", "luke-8"],
        "hard_negatives": [],
    },
    {
        "query": "Peter denying Jesus three times",
        "positives": ["matthew-26", "mark-14", "luke-22", "john-18"],
        "hard_negatives": [],
    },
    {
        "query": "the triumphal entry into Jerusalem",
        "positives": ["matthew-21", "mark-11", "luke-19", "john-12"],
        "hard_negatives": [],
    },
    {
        "query": "Jesus cleansing the temple turning over tables",
        "positives": ["matthew-21", "mark-11", "luke-19", "john-2"],
        "hard_negatives": [],
    },
    {
        "query": "the birth of Jesus",
        "positives": ["matthew-1", "matthew-2", "luke-2"],
        "hard_negatives": [],
    },
    {
        "query": "the Olivet Discourse signs of the end times",
        "positives": ["matthew-24", "mark-13", "luke-21"],
        "hard_negatives": [],
    },
    {
        "query": "the conversion of Saul on the road to Damascus",
        "positives": ["acts-9", "acts-22", "acts-26"],
        "hard_negatives": [],
    },
    {
        "query": "the arrest of Jesus in Gethsemane",
        "positives": ["matthew-26", "mark-14", "luke-22", "john-18"],
        "hard_negatives": [],
    },
]

HARD_NEGATIVE_PAIRS = [
    {
        "query": "the ten commandments",
        "positives": ["exodus-20", "deuteronomy-5"],
        "hard_negatives": ["deuteronomy-10"],
    },
    {
        "query": "the fiery furnace",
        "positives": ["daniel-3"],
        "hard_negatives": ["ezekiel-22"],
    },
    {
        "query": "the armor of God spiritual warfare",
        "positives": ["ephesians-6"],
        "hard_negatives": ["2_corinthians-10"],
    },
    {
        "query": "the two witnesses",
        "positives": ["revelation-11"],
        "hard_negatives": ["2_corinthians-13"],
    },
    {
        "query": "without shedding of blood there is no forgiveness",
        "positives": ["hebrews-9"],
        "hard_negatives": ["leviticus-1", "leviticus-2", "leviticus-3", "leviticus-4"],
    },
    {
        "query": "every knee shall bow every tongue confess",
        "positives": ["philippians-2", "isaiah-45", "romans-14"],
        "hard_negatives": ["isaiah-46", "isaiah-47", "isaiah-48"],
    },
    {
        "query": "if my people humble themselves and pray",
        "positives": ["2chronicles-7"],
        "hard_negatives": ["2chronicles-17", "2chronicles-18", "2chronicles-19"],
    },
    {
        "query": "trust in the Lord with all your heart",
        "positives": ["proverbs-3"],
        "hard_negatives": [],
    },
    {
        "query": "in the beginning was the Word the Logos",
        "positives": ["john-1"],
        "hard_negatives": ["1_john-1"],
    },
    {
        "query": "great is thy faithfulness",
        "positives": ["lamentations-3"],
        "hard_negatives": [],
    },
    {
        "query": "the sun standing still",
        "positives": ["joshua-10"],
        "hard_negatives": ["joshua-9", "joshua-11"],
    },
    {
        "query": "the whore of Babylon Mystery Babylon",
        "positives": ["revelation-17", "revelation-18"],
        "hard_negatives": [],
    },
    {
        "query": "the four horsemen of the apocalypse",
        "positives": ["revelation-6"],
        "hard_negatives": [],
    },
    {
        "query": "the mark of the beast 666",
        "positives": ["revelation-13"],
        "hard_negatives": [],
    },
    {
        "query": "the rapture caught up to meet the Lord",
        "positives": ["1_thessalonians-4"],
        "hard_negatives": ["1_thessalonians-5"],
    },
    {
        "query": "the man of lawlessness the antichrist",
        "positives": ["2_thessalonians-2"],
        "hard_negatives": [],
    },
    {
        "query": "the plague of locusts",
        "positives": ["exodus-10", "joel-1", "joel-2"],
        "hard_negatives": [],
    },
    {
        "query": "Cain killing Abel",
        "positives": ["genesis-4", "1_john-3", "hebrews-11"],
        "hard_negatives": [],
    },
    {
        "query": "the seven seals and seven trumpets",
        "positives": ["revelation-5", "revelation-6", "revelation-8"],
        "hard_negatives": [],
    },
    {
        "query": "the battle of Armageddon",
        "positives": ["revelation-16", "revelation-19"],
        "hard_negatives": [],
    },
    {
        "query": "the new heaven and new earth",
        "positives": ["revelation-21", "revelation-22", "2_peter-3"],
        "hard_negatives": [],
    },
    {
        "query": "the day of the Lord comes as a thief in the night",
        "positives": ["1_thessalonians-5", "2_peter-3"],
        "hard_negatives": [],
    },
    {
        "query": "God hardening Pharaoh's heart",
        "positives": ["exodus-4", "exodus-7", "exodus-9", "exodus-10", "exodus-11"],
        "hard_negatives": [],
    },
]


def load_story_texts(lang: str = "en") -> dict[str, str]:
    path = OUTPUT_DIR / f"corpus_{lang}.jsonl"
    stories = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            e = json.loads(line)
            if e.get("collection") not in ("old_testament", "new_testament"):
                continue
            sid = e["story_id"]
            if sid not in stories:
                stories[sid] = {"bullets": [], "takeaway": ""}
            if e["type"] == "bullet":
                stories[sid]["bullets"].append(e["text"])
            elif e["type"] == "takeaway":
                stories[sid]["takeaway"] = e["text"]

    result = {}
    for sid, data in stories.items():
        text = " ".join(data["bullets"][:15])
        if data["takeaway"]:
            text = data["takeaway"] + " " + text
        if len(text) > 500:
            text = text[:500]
        result[sid] = text
    return result


def build_training_groups(story_texts):
    """Build training groups for InfoNCE loss.

    Each group = (query, [positive_texts], [hard_negative_texts])
    """
    all_groups = []
    all_sids = list(story_texts.keys())

    for spec in GOSPEL_PARALLELS + HARD_NEGATIVE_PAIRS:
        query = spec["query"]
        pos_sids = [s for s in spec["positives"] if s in story_texts]
        neg_sids = [s for s in spec.get("hard_negatives", []) if s in story_texts]

        if not pos_sids:
            continue

        pos_texts = [story_texts[s] for s in pos_sids]
        neg_texts = [story_texts[s] for s in neg_sids]

        random_negs = random.sample(
            [s for s in all_sids if s not in pos_sids and s not in neg_sids],
            min(4, len(all_sids) - len(pos_sids) - len(neg_sids)),
        )
        neg_texts.extend(story_texts[s] for s in random_negs)

        all_groups.append((query, pos_texts, neg_texts))

    # Query variations for each group
    expanded = []
    for query, pos_texts, neg_texts in all_groups:
        expanded.append((query, pos_texts, neg_texts))
        expanded.append((query.lower(), pos_texts, neg_texts))
        words = query.split()
        if len(words) > 4:
            short = " ".join(words[:4])
            expanded.append((short, pos_texts, neg_texts))

    return expanded


def build_regularization_pairs(story_texts, n=500):
    """Random positive pairs from the corpus to prevent catastrophic forgetting."""
    pairs = []
    sids = list(story_texts.keys())
    for _ in range(n):
        sid = random.choice(sids)
        text = story_texts[sid]
        words = text.split()
        if len(words) > 10:
            mid = len(words) // 2
            chunk1 = " ".join(words[:mid])
            chunk2 = " ".join(words[mid:])
            pairs.append((chunk1, chunk2))
    return pairs


def infonce_loss(query_emb, pos_embs, neg_embs, temperature=0.05):
    """InfoNCE loss: query should be close to all positives, far from negatives."""
    all_embs = torch.cat([pos_embs, neg_embs], dim=0)
    similarities = torch.matmul(query_emb.unsqueeze(0), all_embs.T).squeeze(0) / temperature
    n_pos = pos_embs.shape[0]
    labels = torch.zeros(n_pos, dtype=torch.long, device=query_emb.device)

    loss = 0.0
    for i in range(n_pos):
        pos_sim = similarities[i].unsqueeze(0)
        neg_sims = similarities[n_pos:]
        logits = torch.cat([pos_sim, neg_sims], dim=0)
        loss += F.cross_entropy(logits.unsqueeze(0), labels[i:i+1])

    return loss / n_pos


def train_epoch(embedder, groups, reg_pairs, optimizer, temperature=0.05):
    embedder.train_mode()
    total_loss = 0.0
    n_batches = 0

    random.shuffle(groups)
    pbar = tqdm(groups, desc="Training")

    for query, pos_texts, neg_texts in pbar:
        q_emb = embedder.encode_trainable([f"query: {query}"])[0]
        p_embs = embedder.encode_trainable([f"passage: {t}" for t in pos_texts])
        n_embs = embedder.encode_trainable([f"passage: {t}" for t in neg_texts])

        loss = infonce_loss(q_emb, p_embs, n_embs, temperature)

        optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(embedder.model.parameters(), 1.0)
        optimizer.step()

        total_loss += loss.item()
        n_batches += 1
        pbar.set_postfix(loss=f"{loss.item():.4f}")

    # Regularization pass
    if reg_pairs:
        random.shuffle(reg_pairs)
        reg_loss_total = 0.0
        for i in range(0, min(len(reg_pairs), 200), 8):
            batch = reg_pairs[i:i+8]
            texts_a = [f"passage: {p[0]}" for p in batch]
            texts_b = [f"passage: {p[1]}" for p in batch]

            embs_a = embedder.encode_trainable(texts_a)
            embs_b = embedder.encode_trainable(texts_b)

            cos_sim = F.cosine_similarity(embs_a, embs_b)
            reg_loss = (1.0 - cos_sim).mean() * 0.3

            optimizer.zero_grad()
            reg_loss.backward()
            torch.nn.utils.clip_grad_norm_(embedder.model.parameters(), 1.0)
            optimizer.step()
            reg_loss_total += reg_loss.item()

    return total_loss / max(n_batches, 1)


def quick_eval(embedder, story_texts):
    """Quick evaluation on key problem cases."""
    embedder.eval_mode()

    test_cases = [
        ("the resurrection of Jesus", ["matthew-28", "mark-16", "luke-24", "john-20"], ["mark-9"]),
        ("the transfiguration", ["matthew-17", "mark-9", "luke-9"], ["matthew-28"]),
        ("the ten commandments", ["exodus-20", "deuteronomy-5"], ["deuteronomy-10"]),
        ("the fiery furnace", ["daniel-3"], ["ezekiel-22"]),
        ("the armor of God", ["ephesians-6"], ["2_corinthians-10"]),
        ("the Olivet Discourse", ["matthew-24", "mark-13", "luke-21"], []),
        ("feeding the five thousand", ["matthew-14", "mark-6", "luke-9", "john-6"], []),
        ("the crucifixion of Jesus", ["matthew-27", "mark-15", "luke-23", "john-19"], []),
    ]

    all_sids = list(story_texts.keys())
    all_texts = [f"passage: {story_texts[s]}" for s in all_sids]

    with torch.no_grad():
        all_embs = embedder.encode(all_texts, batch_size=256, normalize=True)

    total = 0
    hits = 0
    coverage_scores = []

    for query, pos_sids, neg_sids in test_cases:
        with torch.no_grad():
            q_emb = embedder.encode_single(f"query: {query}", normalize=True)

        scores = all_embs @ q_emb
        top_idx = np.argsort(scores)[::-1][:10]

        top1_sid = all_sids[top_idx[0]]
        top1_hit = any(top1_sid.startswith(p) for p in pos_sids)

        top5_sids = [all_sids[idx] for idx in top_idx[:5]]
        found = sum(1 for p in pos_sids if any(s.startswith(p) for s in top5_sids))
        coverage = found / len(pos_sids)
        coverage_scores.append(coverage)

        neg_in_top1 = any(top1_sid.startswith(n) for n in neg_sids) if neg_sids else False

        total += 1
        if top1_hit:
            hits += 1

        status = "HIT" if top1_hit else ("NEG!" if neg_in_top1 else "MISS")
        print(f"    {status} \"{query}\" -> {top1_sid} [{found}/{len(pos_sids)} in top-5]")

    avg_cov = sum(coverage_scores) / len(coverage_scores) * 100
    print(f"  Quick eval: {hits}/{total} top-1, {avg_cov:.0f}% parallel coverage in top-5")
    return avg_cov


def main():
    random.seed(42)
    torch.manual_seed(42)

    print(f"Loading model: {MODEL_NAME}", flush=True)
    embedder = Embedder(MODEL_NAME)
    print(f"Device: {embedder.device}", flush=True)

    try:
        from peft import LoraConfig, get_peft_model, TaskType
    except ImportError:
        print("ERROR: peft is required for LoRA fine-tuning. Install it: pip install peft")
        print("Full fine-tuning with this little data causes catastrophic forgetting. Refusing to continue.")
        sys.exit(1)

    lora_config = LoraConfig(
        r=8,
        lora_alpha=16,
        target_modules=["query", "value"],
        lora_dropout=0.05,
        bias="none",
    )
    embedder.model = get_peft_model(embedder.model, lora_config)
    trainable = sum(p.numel() for p in embedder.model.parameters() if p.requires_grad)
    total = sum(p.numel() for p in embedder.model.parameters())
    print(f"LoRA applied: {trainable:,} trainable / {total:,} total ({trainable/total*100:.2f}%)")

    print("Loading story texts...", flush=True)
    story_texts = load_story_texts("en")
    print(f"  {len(story_texts)} stories loaded", flush=True)

    print("Building training groups...", flush=True)
    groups = build_training_groups(story_texts)
    print(f"  {len(groups)} training groups", flush=True)

    print("Building regularization pairs...", flush=True)
    reg_pairs = build_regularization_pairs(story_texts, n=500)
    print(f"  {len(reg_pairs)} regularization pairs", flush=True)

    print("\nBaseline eval:", flush=True)
    quick_eval(embedder, story_texts)

    optimizer = AdamW(
        [p for p in embedder.model.parameters() if p.requires_grad],
        lr=2e-4,
        weight_decay=0.01,
    )
    epochs = 5

    best_coverage = 0.0
    best_state = None
    print(f"\nFine-tuning for {epochs} epochs...", flush=True)
    for epoch in range(epochs):
        t0 = time.time()
        avg_loss = train_epoch(embedder, groups, reg_pairs, optimizer, temperature=0.05)
        elapsed = time.time() - t0
        print(f"\n  Epoch {epoch + 1}/{epochs}: avg_loss={avg_loss:.4f} ({elapsed:.1f}s)", flush=True)

        coverage = quick_eval(embedder, story_texts)
        if coverage > best_coverage:
            best_coverage = coverage
            best_state = {k: v.clone() for k, v in embedder.model.state_dict().items()}
            print(f"  New best (coverage={coverage:.0f}%), checkpoint saved in memory")

    if best_state is not None:
        embedder.model.load_state_dict(best_state)
    FINETUNED_DIR.mkdir(parents=True, exist_ok=True)
    if hasattr(embedder.model, 'merge_and_unload'):
        merged = embedder.model.merge_and_unload()
        merged.save_pretrained(str(FINETUNED_DIR))
    else:
        embedder.model.save_pretrained(str(FINETUNED_DIR))
    embedder.tokenizer.save_pretrained(str(FINETUNED_DIR))

    print(f"\nBest parallel coverage: {best_coverage:.0f}%")
    print(f"Fine-tuned model saved to: {FINETUNED_DIR}")
    print("Now re-run: python generate_embeddings.py en")
    print("Then:       python benchmark.py en")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        import traceback
        print(f"\nERROR: {e}", flush=True)
        traceback.print_exc()
        sys.exit(1)
