from pydantic import BaseModel


class BatchResultDTO(BaseModel):
    model_config = {"frozen": True}

    total_processed: int
    exact_duplicates: int
    new_articles: int
    relationships_created: int
    errors: int

    def summary(self) -> str:
        return (
            f"Processed: {self.total_processed}, "
            f"Exact dups: {self.exact_duplicates}, "
            f"New: {self.new_articles}, "
            f"Relationships: {self.relationships_created}, "
            f"Errors: {self.errors}"
        )
