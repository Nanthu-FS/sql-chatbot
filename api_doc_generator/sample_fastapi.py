"""Sample FastAPI application — used as demo input in the doc generator."""

from fastapi import FastAPI, HTTPException, Depends, status, Query
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, EmailStr
from typing import Optional, List
import uuid

app = FastAPI(title="TaskFlow API", version="2.0.0")
security = HTTPBearer()


# ── Schemas ──────────────────────────────────────────────────────────────────

class UserLogin(BaseModel):
    email: str
    password: str

class UserRegister(BaseModel):
    name: str
    email: str
    password: str

class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"

class ProjectCreate(BaseModel):
    name: str
    description: Optional[str] = None
    is_public: bool = False

class Project(ProjectCreate):
    id: str
    owner_id: str
    created_at: str

class TaskCreate(BaseModel):
    title: str
    description: Optional[str] = None
    status: str = "todo"
    priority: str = "medium"
    assignee_id: Optional[str] = None
    due_date: Optional[str] = None

class Task(TaskCreate):
    id: str
    project_id: str
    created_by: str
    created_at: str

class CommentCreate(BaseModel):
    body: str

class UserProfile(BaseModel):
    id: str
    name: str
    email: str
    avatar_url: Optional[str] = None


# ── Auth ─────────────────────────────────────────────────────────────────────

@app.post("/auth/register", response_model=TokenResponse, status_code=201, tags=["auth"])
async def register(payload: UserRegister):
    """Register a new user account."""
    pass

@app.post("/auth/login", response_model=TokenResponse, tags=["auth"])
async def login(payload: UserLogin):
    """Authenticate with email and password to obtain JWT tokens."""
    pass

@app.post("/auth/refresh", response_model=TokenResponse, tags=["auth"])
async def refresh_token(refresh_token: str):
    """Exchange a refresh token for a new access token."""
    pass

@app.post("/auth/logout", status_code=204, tags=["auth"])
async def logout(auth: HTTPAuthorizationCredentials = Depends(security)):
    """Invalidate the current session and revoke the access token."""
    pass

@app.post("/auth/forgot-password", tags=["auth"])
async def forgot_password(email: str):
    """Send a password reset email to the given address."""
    pass

@app.post("/auth/reset-password", tags=["auth"])
async def reset_password(token: str, new_password: str):
    """Reset the user's password using a valid reset token."""
    pass


# ── Users ─────────────────────────────────────────────────────────────────────

@app.get("/users/me", response_model=UserProfile, tags=["users"])
async def get_me(auth: HTTPAuthorizationCredentials = Depends(security)):
    """Get the profile of the currently authenticated user."""
    pass

@app.put("/users/me", response_model=UserProfile, tags=["users"])
async def update_me(
    name: Optional[str] = None,
    avatar_url: Optional[str] = None,
    auth: HTTPAuthorizationCredentials = Depends(security)
):
    """Update the authenticated user's profile information."""
    pass

@app.delete("/users/me", status_code=204, tags=["users"])
async def delete_account(auth: HTTPAuthorizationCredentials = Depends(security)):
    """Permanently delete the authenticated user's account and all data."""
    pass

@app.get("/users/{user_id}", response_model=UserProfile, tags=["users"])
async def get_user(user_id: str, auth: HTTPAuthorizationCredentials = Depends(security)):
    """Get public profile information for a specific user by ID."""
    pass


# ── Projects ──────────────────────────────────────────────────────────────────

@app.get("/projects", response_model=List[Project], tags=["projects"])
async def list_projects(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    search: Optional[str] = None,
    auth: HTTPAuthorizationCredentials = Depends(security)
):
    """List all projects accessible to the authenticated user with pagination."""
    pass

@app.post("/projects", response_model=Project, status_code=201, tags=["projects"])
async def create_project(payload: ProjectCreate, auth: HTTPAuthorizationCredentials = Depends(security)):
    """Create a new project owned by the authenticated user."""
    pass

@app.get("/projects/{project_id}", response_model=Project, tags=["projects"])
async def get_project(project_id: str, auth: HTTPAuthorizationCredentials = Depends(security)):
    """Get details of a specific project by its ID."""
    pass

@app.put("/projects/{project_id}", response_model=Project, tags=["projects"])
async def update_project(
    project_id: str,
    payload: ProjectCreate,
    auth: HTTPAuthorizationCredentials = Depends(security)
):
    """Update a project's metadata. Only the project owner can update."""
    pass

@app.delete("/projects/{project_id}", status_code=204, tags=["projects"])
async def delete_project(project_id: str, auth: HTTPAuthorizationCredentials = Depends(security)):
    """Delete a project and all its tasks. Only the owner can delete."""
    pass


# ── Tasks ─────────────────────────────────────────────────────────────────────

@app.get("/projects/{project_id}/tasks", response_model=List[Task], tags=["tasks"])
async def list_tasks(
    project_id: str,
    status: Optional[str] = None,
    priority: Optional[str] = None,
    assignee_id: Optional[str] = None,
    limit: int = Query(50, ge=1, le=200),
    offset: int = 0,
    auth: HTTPAuthorizationCredentials = Depends(security)
):
    """List tasks in a project with optional filters for status, priority, and assignee."""
    pass

@app.post("/projects/{project_id}/tasks", response_model=Task, status_code=201, tags=["tasks"])
async def create_task(
    project_id: str,
    payload: TaskCreate,
    auth: HTTPAuthorizationCredentials = Depends(security)
):
    """Create a new task within the specified project."""
    pass

@app.get("/projects/{project_id}/tasks/{task_id}", response_model=Task, tags=["tasks"])
async def get_task(
    project_id: str,
    task_id: str,
    auth: HTTPAuthorizationCredentials = Depends(security)
):
    """Get full details of a specific task."""
    pass

@app.put("/projects/{project_id}/tasks/{task_id}", response_model=Task, tags=["tasks"])
async def update_task(
    project_id: str,
    task_id: str,
    payload: TaskCreate,
    auth: HTTPAuthorizationCredentials = Depends(security)
):
    """Update a task's content, status, priority, or assignment."""
    pass

@app.delete("/projects/{project_id}/tasks/{task_id}", status_code=204, tags=["tasks"])
async def delete_task(
    project_id: str,
    task_id: str,
    auth: HTTPAuthorizationCredentials = Depends(security)
):
    """Delete a task from a project."""
    pass

@app.post("/projects/{project_id}/tasks/{task_id}/comments", status_code=201, tags=["tasks"])
async def add_comment(
    project_id: str,
    task_id: str,
    payload: CommentCreate,
    auth: HTTPAuthorizationCredentials = Depends(security)
):
    """Add a comment to a task."""
    pass
