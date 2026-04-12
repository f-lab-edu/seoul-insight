from fastapi import FastAPI

app = FastAPI(title="on-seoul-agent")


@app.get("/health")
def health():
    return {"status": "ok"}
