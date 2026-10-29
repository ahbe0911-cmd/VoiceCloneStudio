"""Experimental Persian voice-cloning endpoint. Requires a compatible XTTS model and ffmpeg."""
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.responses import FileResponse
from pathlib import Path
from tempfile import TemporaryDirectory
import os
import subprocess
import threading

app = FastAPI(title="VoiceCloneStudio API")
_model = None
_lock = threading.Lock()
MODEL_NAME = os.getenv("VOICE_MODEL", "MohammadJRanjbar/ParsVoice-XTTS")

@app.get("/health")
def health():
    return {"status": "ok", "engine": MODEL_NAME, "loaded": _model is not None}

def get_model():
    global _model
    with _lock:
        if _model is None:
            from TTS.api import TTS
            import torch
            # Model compatibility depends on the published checkpoint and installed TTS version.
            _model = TTS(MODEL_NAME).to("cuda" if torch.cuda.is_available() else "cpu")
        return _model

@app.post("/clone")
def clone(text: str = Form(...), reference: UploadFile = File(...)):
    if not text.strip() or len(text) > 1000:
        raise HTTPException(400, "Text must contain 1 to 1000 characters")
    with TemporaryDirectory() as tmp:
        folder = Path(tmp)
        source = folder / "input_audio"
        total = 0
        with source.open("wb") as out:
            while chunk := reference.file.read(1024 * 1024):
                total += len(chunk)
                if total > 15 * 1024 * 1024:
                    raise HTTPException(413, "Reference audio exceeds 15 MB")
                out.write(chunk)
        if not total:
            raise HTTPException(400, "Reference audio is empty")
        wav = folder / "reference.wav"
        output = folder / "result.wav"
        try:
            subprocess.run(
                ["ffmpeg", "-nostdin", "-v", "error", "-y", "-i", str(source),
                 "-ac", "1", "-ar", "24000", str(wav)],
                check=True, timeout=40, capture_output=True
            )
            model = get_model()
            model.tts_to_file(text=text, speaker_wav=str(wav), language="fa", file_path=str(output))
            if not output.exists() or output.stat().st_size == 0:
                raise RuntimeError("Model produced no audio")
            # Read bytes before temp directory is removed.
            from fastapi.responses import Response
            return Response(content=output.read_bytes(), media_type="audio/wav")
        except subprocess.CalledProcessError:
            raise HTTPException(400, "Reference audio could not be decoded")
        except subprocess.TimeoutExpired:
            raise HTTPException(408, "Audio conversion timed out")
        except Exception as exc:
            # Do not leak server paths or model details to callers.
            print("Inference failure:", repr(exc), flush=True)
            raise HTTPException(503, "Model initialization or synthesis failed; check server logs")
