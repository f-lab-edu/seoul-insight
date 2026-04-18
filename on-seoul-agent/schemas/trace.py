from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field


class AgentTrace(BaseModel):
    message_id: int
    trace: dict[str, Any]
    created_at: datetime = Field(default_factory=datetime.now)
