import sys
print(f"Python: {sys.executable}", flush=True)
print(f"Path[0]: {sys.path[0]}", flush=True)

print("1. importing json...", flush=True)
import json

print("2. importing torch...", flush=True)
import torch

print("3. importing huggingface_hub...", flush=True)
try:
    import huggingface_hub
    print(f"   version: {huggingface_hub.__version__}", flush=True)
except BaseException as e:
    print(f"   FAILED: {type(e).__name__}: {e}", flush=True)

print("4. importing transformers...", flush=True)
try:
    import transformers
    print(f"   version: {transformers.__version__}", flush=True)
except BaseException as e:
    print(f"   FAILED: {type(e).__name__}: {e}", flush=True)

print("5. importing sentence_transformers...", flush=True)
try:
    import sentence_transformers
    print(f"   version: {sentence_transformers.__version__}", flush=True)
except BaseException as e:
    print(f"   FAILED: {type(e).__name__}: {e}", flush=True)

print("DONE", flush=True)
