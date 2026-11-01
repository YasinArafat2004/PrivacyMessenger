const express = require("express");
const Database = require("better-sqlite3");
const path = require("path");

const app = express();
const PORT = 3000;

app.use(express.json());

// Create or open the persistent database file.
const dbPath = path.join(__dirname, "privacymessenger.db");
const db = new Database(dbPath);

// Create the messages table if it does not exist.
db.exec(`
    CREATE TABLE IF NOT EXISTS messages (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        senderUsername TEXT NOT NULL,
        receiverUsername TEXT NOT NULL,
        content TEXT NOT NULL,
        timestamp TEXT NOT NULL
    )
`);

// Health-check endpoint
app.get("/api/health", (req, res) => {
    res.status(200).json({
        success: true,
        message: "PrivacyMessenger API is running!"
    });
});

// Create a message and save it in SQLite.
app.post("/api/messages", (req, res) => {
    const { senderUsername, receiverUsername, content } = req.body;

    if (
        typeof senderUsername !== "string" ||
        !senderUsername.trim() ||
        typeof receiverUsername !== "string" ||
        !receiverUsername.trim() ||
        typeof content !== "string" ||
        !content.trim()
    ) {
        return res.status(400).json({
            success: false,
            message: "Sender, receiver, and message content are required."
        });
    }

    const timestamp = new Date().toISOString();

    const result = db.prepare(`
        INSERT INTO messages
            (senderUsername, receiverUsername, content, timestamp)
        VALUES
            (?, ?, ?, ?)
    `).run(
        senderUsername.trim(),
        receiverUsername.trim(),
        content.trim(),
        timestamp
    );

    const message = db.prepare(`
        SELECT id, senderUsername, receiverUsername, content, timestamp
        FROM messages
        WHERE id = ?
    `).get(result.lastInsertRowid);

    return res.status(201).json({
        success: true,
        message: "Message saved successfully.",
        data: message
    });
});

// Retrieve messages involving a username.
app.get("/api/messages", (req, res) => {
    const username = req.query.username;

    if (typeof username !== "string" || !username.trim()) {
        return res.status(400).json({
            success: false,
            message: "Provide a username query parameter."
        });
    }

    const userMessages = db.prepare(`
        SELECT id, senderUsername, receiverUsername, content, timestamp
        FROM messages
        WHERE senderUsername = ? OR receiverUsername = ?
        ORDER BY id ASC
    `).all(username.trim(), username.trim());

    return res.status(200).json({
        success: true,
        count: userMessages.length,
        data: userMessages
    });
});

// Start the API server.
const server = app.listen(PORT, "0.0.0.0", () => {
    console.log("PrivacyMessenger API running on port " + PORT);
    console.log("SQLite database: " + dbPath);
});

// Close resources cleanly when stopping the server.
function shutdown() {
    server.close(() => {
        db.close();
        process.exit(0);
    });
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);

