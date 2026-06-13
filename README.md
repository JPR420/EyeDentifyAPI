# ⚙️ EyeDentifyAPI: Ktor Backend (API Orchestration)

[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/JPR420/EyeDentifyAPI/blob/main/LICENSE)
[![Ktor](https://img.shields.io/badge/Ktor-00A9F9.svg?logo=kotlin&logoColor=white)](https://ktor.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192.svg?logo=postgresql&logoColor=white)](https://www.postgresql.org/)

This repository contains the backend server for the EyeDentify mobile application, built using the **Ktor** framework in Kotlin. Its primary function is to securely manage user data, handle file uploads, and orchestrate calls to multiple specialized AI services.

## 🔗 Related Repository

* **[EyeDentify (Android Client)](https://github.com/JPR420/EyeDentify)**

## 🚀 Key Technologies & Services

| Technology | Purpose |
| :--- | :--- |
| **Ktor Framework** | Asynchronous server engine (Netty) for routing and handling multipart requests. |
| **PostgreSQL / JDBC** | Data persistence for users (BCrypt hashing) and history storage (BLOB/BYTEA). |
| **BCrypt** | Used for secure, one-way hashing of user passwords during registration and login. |
| **Google Vision API** | Primary general object identification service. |
| **OpenAI (GPT-4o)** | Advanced multi-modal identification and structured JSON response generation. |
| **Pl@ntNet API** | Specialized identification service for plants, flowers, and trees. |



## 💻 Running the Backend

### Prerequisites

* JVM (Java Development Kit) installed.
* A running PostgreSQL instance.
* API Keys for Google Vision, OpenAI, and Pl@ntNet.

### Setup and Configuration

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/JPR420/EyeDentifyAPI.git
    cd EyeDentifyAPI
    ```
2.  **Database Setup:**
    Ensure your PostgreSQL instance is running. The server attempts to create the required `users` and `capture_results` tables on startup (code is commented out in `main` for safety, may need manual execution).

3.  **Credential Management:**
    You must configure your database and API credentials in the `DatabaseConfig.kt` file (or a secure environment variable system):
    * `jdbcUrl`, `username`, `password` (for PostgreSQL)
    * `googleKey`, `openAiKey`, `plantNetKey`

4.  **Start the Server:**
    Run the `main` function in your IDE, or use Gradle:
    ```bash
    ./gradlew run
    # Server will start on http://localhost:8080
    ```

## 🗺️ API Endpoints

| Endpoint | Method | Role | Body/Params |
| :--- | :--- | :--- | :--- |
| `/register` | `POST` | Creates a new user with BCrypt hashed password. | `email`, `password` (Form Parameters) |
| `/login` | `POST` | Authenticates user and returns `id` and `tier`. | `email`, `password` (Form Parameters) |
| `/identify` | `POST` | General object recognition via **Google Vision**. | Multipart (Image File) |
| `/identify_openai` | `POST` | Detailed object recognition via **GPT-4o-mini**. | Multipart (Image File) |
| `/identify_plantnet` | `POST` | Plant/Nature identification via **Pl@ntNet**. | Multipart (Image File, optional `organs`) |
| `/saveResult` | `POST` | Saves captured data and image to user history. | Multipart (Image File + Form data: `user_id`, `object_name`, etc.) |
| `/getUserHistory` | `POST` | Retrieves history for a user, returning images as Base64. | `user_id` (Form Parameter) |
