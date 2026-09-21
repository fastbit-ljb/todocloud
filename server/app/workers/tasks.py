from app.workers.celery_app import celery_app


@celery_app.task(name="todocloud.health_check")
def health_check_task() -> str:
    """Worker wiring smoke test; real AI tasks will be added in the next phase."""
    return "ok"

