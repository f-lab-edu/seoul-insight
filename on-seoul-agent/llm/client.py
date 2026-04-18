from langchain_core.embeddings import Embeddings
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from core.config import settings


def get_chat_model(
    model: str | None = None,
    temperature: float = 0.0,
    streaming: bool = False,
) -> BaseChatModel:
    """Return a configured chat LLM instance.

    Defaults to the model from settings. Pass model= to override.
    """
    return ChatOpenAI(
        api_key=settings.openai_api_key,
        model=model or settings.openai_model,
        temperature=temperature,
        streaming=streaming,
    )


def get_embeddings(model: str | None = None) -> Embeddings:
    """Return a configured embeddings instance."""
    return OpenAIEmbeddings(
        api_key=settings.openai_api_key,
        model=model or settings.embedding_model,
    )
