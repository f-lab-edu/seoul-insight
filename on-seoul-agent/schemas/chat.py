from pydantic import BaseModel

from schemas.state import IntentType


class ChatRequest(BaseModel):
    user_id: str
    session_id: str
    message: str


class ChatResponse(BaseModel):
    session_id: str
    message: str
    intent: IntentType | None = None
