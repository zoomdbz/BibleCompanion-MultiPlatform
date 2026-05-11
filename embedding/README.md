# Bible Companion Embedding Pipeline (Phase 1)

Offline semantic search: pre-compute embeddings for all verse bullets,
export ONNX model + vector matrices for on-device inference.

## Setup (Windows with CUDA)

```powershell
cd "G:\Bible Companion App\embedding-project"
python -m venv venv
.\venv\Scripts\activate
pip install torch --index-url https://download.pytorch.org/whl/cu121
pip install -r requirements.txt
```

## Steps

Run from `G:\Bible Companion App\embedding-project`:

```powershell
# 1. Parse app JSON into corpus JSONL files (~30 sec)
python parse_corpus.py

# 2. Embed all bullets with multilingual-e5-small on GPU (~5-10 min total)
python generate_embeddings.py

# 3. Export ONNX model with tokenizer
python export_model.py

# 4. Benchmark search quality against 50 concept queries
python benchmark.py en
python benchmark.py en ar es fr de it ja ko pt ru hi zh-Hans zh-Hant
```

You can also embed specific languages:
```powershell
python generate_embeddings.py en es ar
```

## Output

All generated assets land in `output/`:
- `corpus_{lang}.jsonl` -- parsed bullets with metadata
- `embeddings_{lang}.bin` -- int8 quantized vector matrices (header + N x 384 int8)
- `metadata_{lang}.json` -- vector position to story ID mapping
- `model/` -- ONNX model with tokenizer files
- `benchmark_report.json` -- detailed accuracy results

## Binary format (embeddings_{lang}.bin)

```
Offset  Size  Field
0       4     Magic: "BCEF"
4       4     Version: uint32 LE (1)
8       4     Count: uint32 LE (number of vectors)
12      4     Dim: uint32 LE (384)
16      4     Scale: float32 LE (quantization scale)
20      4     Offset: float32 LE (quantization offset)
24      N*384 Data: int8 vectors
```

Dequantize: `float_val = (int8_val + 127) * scale + offset`
