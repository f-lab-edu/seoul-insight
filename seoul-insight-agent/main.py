from fastapi import FastAPI

app = FastAPI(title="seoul-insight-agent")


@app.get("/health")
def health():
    return {"status": "ok"}
