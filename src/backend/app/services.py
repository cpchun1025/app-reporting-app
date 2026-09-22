import logging
from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from apscheduler.schedulers.background import BackgroundScheduler

logger = logging.getLogger(__name__)


class ConsoleEmailProvider:
    """Development mail implementation that writes messages to application logs."""

    def send(self, recipient: str, subject: str, body: str) -> None:
        logger.info("Console email to=%s subject=%s body=%s", recipient, subject, body)


@dataclass
class ScheduledJobs:
    email_provider: ConsoleEmailProvider

    def send_daily_report_reminder(self) -> None:
        today = date.today().isoformat()
        self.email_provider.send(
            recipient="operations@example.invalid",
            subject=f"Trading report reminder: {today}",
            body="Confirm that today's booked trades have been reviewed.",
        )


def configure_scheduler(jobs: ScheduledJobs) -> BackgroundScheduler:
    scheduler = BackgroundScheduler(timezone="UTC")
    scheduler.add_job(
        jobs.send_daily_report_reminder,
        trigger="cron",
        hour=18,
        minute=0,
        id="daily-report-reminder",
        replace_existing=True,
    )
    return scheduler


ZERO = Decimal("0")
