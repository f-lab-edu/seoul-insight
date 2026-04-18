from langchain_core.embeddings import Embeddings
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from core.config import settings


def get_chat_model(
    provider: str | None = None,
    model: str | None = None,
    temperature: float = 0.0,
    streaming: bool = False,
) -> BaseChatModel:
    """Return a configured chat LLM instance.

    Gemini를 기본으로 사용하고, provider="openai" 지정 시 GPT로 전환한다.
    """
    selected_provider = provider or settings.llm_provider

    if selected_provider == "gemini":
        return ChatGoogleGenerativeAI(
            google_api_key=settings.google_api_key,
            model=model or settings.gemini_model,
            temperature=temperature,
        )
    elif selected_provider == "openai":
        return ChatOpenAI(
            api_key=settings.openai_api_key,
            model=model or settings.gpt_model,
            temperature=temperature,
            streaming=streaming,
        )
    else:
        raise ValueError(
            f"Unknown LLM provider: {selected_provider!r}. Use 'gemini' or 'openai'."
        )


def get_embeddings(model: str | None = None) -> Embeddings:
    """Return a configured embeddings instance.

    임베딩은 OpenAI 고정 (DDL vector(1536) 기준).
    """
    return OpenAIEmbeddings(
        api_key=settings.openai_api_key,
        model=model or settings.embedding_model,
    )
