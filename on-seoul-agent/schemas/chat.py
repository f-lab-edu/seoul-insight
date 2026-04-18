from pydantic import BaseModel


class ChatRequest(BaseModel):
    user_id: str
    session_id: str
    message: str


class ChatResponse(BaseModel):
    session_id: str
    message: str
    intent: str | None = None  # SQL_SEARCH, VECTOR_SEARCH, MAP, FALLBACK
