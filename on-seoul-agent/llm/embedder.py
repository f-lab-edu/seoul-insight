from langchain_core.embeddings import Embeddings

from llm.client import get_embeddings


class Embedder:
    def __init__(self, embeddings: Embeddings | None = None) -> None:
        self._embeddings = embeddings or get_embeddings()

    async def embed(self, text: str) -> list[float]:
        """Embed a single text string and return the vector."""
        return await self._embeddings.aembed_query(text)

    async def embed_many(self, texts: list[str]) -> list[list[float]]:
        """Embed multiple texts and return a list of vectors."""
        return await self._embeddings.aembed_documents(texts)
