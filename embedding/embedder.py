#!/usr/bin/env python3
"""Shared embedding utilities using transformers directly (no sentence-transformers dependency)."""

import numpy as np
import torch
from transformers import AutoModel, AutoTokenizer


class Embedder:
    """Wraps a transformer model for text embedding with mean pooling and L2 normalization."""

    def __init__(self, model_name_or_path, device=None):
        if device is None:
            device = "cuda" if torch.cuda.is_available() else "cpu"
        self.device = device
        self.tokenizer = AutoTokenizer.from_pretrained(model_name_or_path)
        self.model = AutoModel.from_pretrained(model_name_or_path).to(device)
        self.model.eval()
        self.embedding_dim = self.model.config.hidden_size

    def encode(self, texts, batch_size=32, normalize=True, show_progress=False):
        all_embeddings = []

        batches = range(0, len(texts), batch_size)
        if show_progress:
            from tqdm import tqdm
            batches = tqdm(batches, desc="Embedding",
                           total=(len(texts) + batch_size - 1) // batch_size)

        for i in batches:
            batch = texts[i:i + batch_size]
            inputs = self.tokenizer(
                batch, padding=True, truncation=True,
                max_length=512, return_tensors="pt",
            ).to(self.device)

            with torch.no_grad():
                outputs = self.model(**inputs)

            token_embs = outputs.last_hidden_state
            mask = inputs["attention_mask"].unsqueeze(-1).float()
            pooled = (token_embs * mask).sum(1) / mask.sum(1).clamp(min=1e-9)

            if normalize:
                pooled = torch.nn.functional.normalize(pooled, p=2, dim=1)

            all_embeddings.append(pooled.cpu().numpy())

        return np.concatenate(all_embeddings, axis=0)

    def encode_single(self, text, normalize=True):
        return self.encode([text], normalize=normalize)[0]

    def get_embedding_dimension(self):
        return self.embedding_dim

    def save(self, path):
        self.model.save_pretrained(path)
        self.tokenizer.save_pretrained(path)

    def train_mode(self):
        self.model.train()
        return self

    def eval_mode(self):
        self.model.eval()
        return self

    def encode_trainable(self, texts):
        """Encode with gradients enabled (for fine-tuning). Returns torch tensor on device."""
        inputs = self.tokenizer(
            texts, padding=True, truncation=True,
            max_length=512, return_tensors="pt",
        ).to(self.device)
        outputs = self.model(**inputs)
        token_embs = outputs.last_hidden_state
        mask = inputs["attention_mask"].unsqueeze(-1).float()
        pooled = (token_embs * mask).sum(1) / mask.sum(1).clamp(min=1e-9)
        return torch.nn.functional.normalize(pooled, p=2, dim=1)
