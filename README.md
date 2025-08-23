Rocket.Chat Spring Boot Integration

This repository contains a Spring Boot-based integration with a self-hosted Rocket.Chat instance using REST APIs and WebSockets, with PostgreSQL for data storage inside the Rocket.Chat container.

📌 Overview:
This project provides an API layer that interacts with a locally hosted Rocket.Chat server, enabling features such as:
Admin authentication and session management
User creation and login
Channel and direct message interactions
Message sending and retrieval
Webhook simulation for message events

The following diagram shows the overall architecture and flow:

<img width="3840" height="918" alt="Rocket-Chat_Flow_Diagram" src="https://github.com/user-attachments/assets/283a03b1-78a9-4939-9809-19763de56d88" />

🚀 Features
1️⃣ Admin Operations
Login – Authenticates as an admin to interact with the Rocket.Chat server.
Token Management – Automatically manages authToken and userId for admin APIs.

2️⃣ User Operations
User Login – Allows standard users to log in and receive a session token.
Create User – Creates new Rocket.Chat users via admin privileges.

3️⃣ Messaging Operations
Send Messages – Post messages to channels or direct rooms.
Retrieve Messages – Fetch message history from channels or direct message rooms.

4️⃣ Websocket Simulation
Simulates receiving a message payload for testing purposes.
Validates requests with a token header for security.

🛠 Tech Stack
Layer	Technology
Backend Framework	Spring Boot 3
Database	PostgreSQL (inside Rocket.Chat container)
Communication	Rocket.Chat REST APIs, WebSocket for real-time
Logging	SLF4J + Logback
Build Tools	Maven

📂 Project Structure
Layer	Description
Controller Layer	Exposes REST APIs (e.g., ChatController)
Service Layer	Business logic for Admin and User operations
DTOs	Data transfer objects for requests and responses
Exception Layer	Custom exceptions with global handler
Configuration	Application properties, Rocket.Chat integration configs

⚡ How to Run
1️⃣ Clone the repository
git clone https://github.com/gautamkr538/Rocket.Chat.git
cd rocket-chat-integration

2️⃣ Configure Environment
Update your application.properties:
rocket.chat.base-url=http://localhost:3000
rocket.chat.admin.username=admin
rocket.chat.admin.password=admin_password
server.port=8080

3️⃣ Start Your Local Rocket.Chat:
If you are running Rocket.Chat locally with Docker Compose:
docker-compose up -d
Make sure PostgreSQL is running inside the Rocket.Chat container.

4️⃣ Build and Run
./mvnw clean install
./mvnw spring-boot:run

5️⃣ Access API Documentation:
After the app runs, visit Swagger UI:
http://localhost:8080/swagger-ui.html

📜 Endpoints Summary:
Endpoint	Method	Description
/chat/login	POST	Login as Admin
/chat/user-login	POST	Login as a regular user
/chat/create-user	POST	Create a new user
/chat/send	POST	Send message to a room
/chat/messages	GET	Get messages from a room
/chat/get-direct-messages	GET	Retrieve direct messages
/chat/create-direct-message-room	POST	Create a DM room with a user
/chat/simulate-message	POST	Simulate receiving webhook messages

🔗 External Integrations:
Rocket.Chat REST API – For authentication, messaging, and user management
PostgreSQL – Database for Rocket.Chat data storage
WebSockets – Real-time message updates

🧩 Future Enhancements:
Add role-based authentication and authorization
Extend support for file attachments in messages
Implement real-time WebSocket listeners in the Spring Boot service
Add monitoring and analytics with Prometheus + Grafana
