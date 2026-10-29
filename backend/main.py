"""API contract for a separately hosted Persian voice-cloning model."""
from fastapi import FastAPI, UploadFile, File, Form, HTTPException

app = FastAPI(title="VoiceCloneStudio API")

@app.get("/health")
def health():
    return {"status": "ok", "engine": "not_configured"}

@app.post("/clone")
async def clone(text: str = Form(...), reference: UploadFile = File(...)):
    if not text.strip():
        raise HTTPException(status_code=400, detail="Text is required")
    if len(text) > 5000:
        raise HTTPException(status_code=413, detail="Text is too long")
    # Deliberately do not return fake or unrelated synthesized speech.
    # Plug a verified Persian-capable, consent-based cloning engine in here.
    raise HTTPException(
        status_code=501,
        detail="Persian voice-cloning engine is not configured yet",
    )
