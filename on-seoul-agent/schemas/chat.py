from pydantic import BaseModel

from schemas.state import IntentType


class ChatRequest(BaseModel):
    room_id: int
    message_id: int
    message: str


class ChatResponse(BaseModel):
    message_id: int
    message: str
    intent: IntentType | None = None
