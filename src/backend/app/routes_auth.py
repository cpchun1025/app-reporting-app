from typing import Annotated

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select

from app.config import get_settings
from app.dependencies import CurrentUser, DbSession
from app.models import User
from app.schemas import CurrentUserResponse, MockCallbackRequest, TokenRequest, TokenResponse
from app.security import create_access_token, verify_password

router = APIRouter(prefix="/auth", tags=["authentication"])


def token_response(user: User) -> TokenResponse:
    return TokenResponse(access_token=create_access_token(user.id))


@router.post("/login", response_model=TokenResponse)
def login(payload: TokenRequest, db: DbSession) -> TokenResponse:
    user = db.scalar(select(User).where(User.username == payload.username))
    if user is None or not user.is_active or not verify_password(payload.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username or password.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return token_response(user)


@router.post("/mock/callback", response_model=TokenResponse)
def mock_callback(payload: MockCallbackRequest, db: DbSession) -> TokenResponse:
    if not get_settings().seed_dev_users:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Mock callback is disabled.")
    user = db.scalar(select(User).where(User.username == payload.username))
    if user is None or not user.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Unknown development user.")
    return token_response(user)


@router.get("/me", response_model=CurrentUserResponse)
def current_user(current_user: CurrentUser) -> CurrentUserResponse:
    return CurrentUserResponse(
        username=current_user.username,
        role="admin" if current_user.is_admin else "trader",
    )
