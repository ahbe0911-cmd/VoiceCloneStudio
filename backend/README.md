# VoiceCloneStudio backend contract

This is an API skeleton, **not a deployed or functioning voice-cloning engine**.

Run locally:

```bash
cd backend
python -m pip install -r requirements.txt
uvicorn main:app --host 127.0.0.1 --port 8000
```

The Android client requires a publicly reachable HTTPS URL. Localhost on a laptop is not reachable as localhost from a phone. Use a properly secured HTTPS deployment or tunnel for testing.

`POST /clone`: multipart form fields `text` (Persian text) and `reference` (audio file). On success return raw WAV audio with `Content-Type: audio/wav`. The current endpoint returns HTTP 501 intentionally until a verified Persian-capable model is integrated.

Before public deployment add authentication, consent verification, file-size and duration limits, rate limiting, safe upload handling, and privacy-preserving retention policies. Do not clone anyone without authorization.
