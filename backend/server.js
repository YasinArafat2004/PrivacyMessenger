const express = require("express");
const { Pool } = require("pg");
const { createServer } = require("http");
const { Server } = require("socket.io");
require("dotenv").config();

const app = express();
const PORT = process.env.PORT || 3000;
const MAX_CONTENT = 4000;

app.use(express.json({ limit: "1mb" }));

// ==========================================
// 1. DATABASE CONNECTION
// ==========================================

if (!process.env.DATABASE_URL) {
    console.error("DATABASE_URL is missing. Check your backend .env file.");
    process.exit(1);
}

const pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: { rejectUnauthorized: false }
});

async function setupDatabase() {
    await pool.query(`
        CREATE TABLE IF NOT EXISTS friend_requests (
            id SERIAL PRIMARY KEY,
            sender_username VARCHAR(50) NOT NULL,
            sender_name VARCHAR(100) NOT NULL,
            receiver_username VARCHAR(50) NOT NULL,
            receiver_name VARCHAR(100) NOT NULL,
            status VARCHAR(20) DEFAULT 'PENDING',
            created_at TIMESTAMPTZ DEFAULT NOW(),
            updated_at TIMESTAMPTZ DEFAULT NOW()
        );
    `);

    // The existing users table is used as-is.
    await pool.query("SELECT username FROM users LIMIT 1");

    console.log("Database connection verified.");
}

// ==========================================
// 2. HTTP ROUTES
// ==========================================

app.get("/api/health", (_req, res) => {
    res.json({
        success: true,
        message: "PrivacyMessenger API running"
    });
});

// Register a user.
app.post("/api/users/register", async (req, res) => {
    const { username, name } = req.body || {};

    if (typeof username !== "string" || typeof name !== "string") {
        return res.status(400).json({
            success: false,
            message: "Missing fields"
        });
    }

    const u = username.trim();
    const n = name.trim();

    if (!u || u.length > 50 || !/^[a-zA-Z0-9_]+$/.test(u)) {
        return res.status(400).json({
            success: false,
            message: "Invalid username"
        });
    }

    if (!n || n.length > 100) {
        return res.status(400).json({
            success: false,
            message: "Invalid name"
        });
    }

    try {
        const result = await pool.query(
            `INSERT INTO users (username, actual_name)
             VALUES ($1, $2)
             ON CONFLICT (username)
             DO UPDATE SET actual_name = EXCLUDED.actual_name
             RETURNING username, actual_name AS name`,
            [u, n]
        );

        console.log(`[register] ${u} (${n})`);

        res.json({
            success: true,
            data: result.rows[0]
        });
    } catch (err) {
        console.error("Register error:", err.message);

        res.status(500).json({
            success: false,
            message: "Database error"
        });
    }
});

// Search for a registered user.
app.get("/api/users/search", async (req, res) => {
    const username = (req.query.username || "").toString().trim();

    if (!username) {
        return res.status(400).json({
            success: false,
            message: "Missing username"
        });
    }

    try {
        const result = await pool.query(
            `SELECT username, actual_name AS name
             FROM users
             WHERE username = $1`,
            [username]
        );

        res.json({
            success: true,
            data: result.rows.length ? result.rows[0] : null
        });
    } catch (err) {
        console.error("Search error:", err.message);

        res.status(500).json({
            success: false,
            message: "Database error"
        });
    }
});

// Send a friend request.
app.post("/api/friend-requests", async (req, res) => {
    const { fromUsername, toUsername } = req.body || {};

    if (
        typeof fromUsername !== "string" ||
        typeof toUsername !== "string"
    ) {
        return res.status(400).json({
            success: false,
            message: "Missing fields"
        });
    }

    const from = fromUsername.trim();
    const to = toUsername.trim();

    if (!from || !to) {
        return res.status(400).json({
            success: false,
            message: "Missing usernames"
        });
    }

    if (from === to) {
        return res.status(400).json({
            success: false,
            message: "Cannot add yourself"
        });
    }

    try {
        const fromUser = await pool.query(
            "SELECT actual_name FROM users WHERE username = $1",
            [from]
        );

        const toUser = await pool.query(
            "SELECT actual_name FROM users WHERE username = $1",
            [to]
        );

        if (fromUser.rows.length === 0) {
            return res.status(404).json({
                success: false,
                message: "Sender not registered"
            });
        }

        if (toUser.rows.length === 0) {
            return res.status(404).json({
                success: false,
                message: "Receiver not registered"
            });
        }

        const existing = await pool.query(
            `SELECT id, status
             FROM friend_requests
             WHERE sender_username = $1
             AND receiver_username = $2`,
            [from, to]
        );

        if (existing.rows.length > 0) {
            const request = existing.rows[0];

            if (request.status === "PENDING") {
                return res.json({
                    success: true,
                    data: {
                        id: request.id,
                        status: "PENDING"
                    }
                });
            }

            await pool.query(
                `UPDATE friend_requests
                 SET status = 'PENDING', updated_at = NOW()
                 WHERE id = $1`,
                [request.id]
            );

            return res.json({
                success: true,
                data: {
                    id: request.id,
                    status: "PENDING"
                }
            });
        }

        const insert = await pool.query(
            `INSERT INTO friend_requests
             (sender_username, sender_name, receiver_username,
              receiver_name, status)
             VALUES ($1, $2, $3, $4, 'PENDING')
             RETURNING id`,
            [from, fromUser.rows[0].actual_name, to, toUser.rows[0].actual_name]
        );

        console.log(`[friend-request] ${from} -> ${to}`);

        res.json({
            success: true,
            data: {
                id: insert.rows[0].id,
                status: "PENDING"
            }
        });
    } catch (err) {
        console.error("Friend request error:", err.message);

        res.status(500).json({
            success: false,
            message: "Database error"
        });
    }
});

// Get received friend requests.
app.get("/api/friend-requests/received", async (req, res) => {
    const username = (req.query.username || "").toString().trim();

    if (!username) {
        return res.status(400).json({
            success: false,
            message: "Missing username"
        });
    }

    try {
        const result = await pool.query(
            `SELECT id,
                    sender_username AS "senderUsername",
                    sender_name AS "senderName",
                    created_at AS "createdAt"
             FROM friend_requests
             WHERE receiver_username = $1
             AND status = 'PENDING'
             ORDER BY id ASC`,
            [username]
        );

        res.json({
            success: true,
            data: result.rows
        });
    } catch (err) {
        console.error("Received requests error:", err.message);

        res.status(500).json({
            success: false,
            message: "Database error"
        });
    }
});

// Get sent friend requests.
app.get("/api/friend-requests/sent", async (req, res) => {
    const username = (req.query.username || "").toString().trim();

    if (!username) {
        return res.status(400).json({
            success: false,
            message: "Missing username"
        });
    }

    try {
        const result = await pool.query(
            `SELECT id,
                    receiver_username AS "receiverUsername",
                    receiver_name AS "receiverName",
                    status,
                    updated_at AS "updatedAt"
             FROM friend_requests
             WHERE sender_username = $1
             ORDER BY id ASC`,
            [username]
        );

        res.json({
            success: true,
            data: result.rows
        });
    } catch (err) {
        console.error("Sent requests error:", err.message);

        res.status(500).json({
            success: false,
            message: "Database error"
        });
    }
});

// Accept or reject a friend request.
app.post("/api/friend-requests/:id/respond", async (req, res) => {
    const id = Number.parseInt(req.params.id, 10);
    const { username, accepted } = req.body || {};

    if (
        !Number.isInteger(id) ||
        id <= 0 ||
        typeof username !== "string" ||
        typeof accepted !== "boolean"
    ) {
        return res.status(400).json({
            success: false,
            message: "Missing or invalid fields"
        });
    }

    try {
        const check = await pool.query(
            `SELECT receiver_username, status
             FROM friend_requests
             WHERE id = $1`,
            [id]
        );

        if (check.rows.length === 0) {
            return res.status(404).json({
                success: false,
                message: "Not found"
            });
        }

        if (check.rows[0].receiver_username !== username.trim()) {
            return res.status(403).json({
                success: false,
                message: "Not your request"
            });
        }

        if (check.rows[0].status !== "PENDING") {
            return res.status(400).json({
                success: false,
                message: "Already responded"
            });
        }

        const newStatus = accepted ? "ACCEPTED" : "REJECTED";

        await pool.query(
            `UPDATE friend_requests
             SET status = $1, updated_at = NOW()
             WHERE id = $2`,
            [newStatus, id]
        );

        console.log(`[friend-response] ${username} ${newStatus} request#${id}`);

        res.json({
            success: true,
            data: {
                id,
                status: newStatus
            }
        });
    } catch (err) {
        console.error("Respond error:", err.message);

        res.status(500).json({
            success: false,
            message: "Database error"
        });
    }
});

// Old HTTP message endpoints are intentionally disabled.
app.all("/api/messages", (_req, res) => {
    res.status(410).json({
        success: false,
        message: "HTTP messaging is disabled. Use the Socket.IO message relay."
    });
});

// ==========================================
// 3. SOCKET.IO SERVER
// ==========================================

const httpServer = createServer(app);

const io = new Server(httpServer, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

// username -> Set of socket IDs (online presence only)
const activeUsers = new Map();

// socket ID -> username
const socketUsers = new Map();

// 🔴 NEW: username -> username of the person they are currently in a call with
const activeCalls = new Map();

function addOnlineUser(username, socketId) {
    if (!activeUsers.has(username)) {
        activeUsers.set(username, new Set());
    }

    activeUsers.get(username).add(socketId);
    socketUsers.set(socketId, username);
}

function removeOnlineUser(socketId) {
    const username = socketUsers.get(socketId);

    if (!username) return;

    const sockets = activeUsers.get(username);

    if (sockets) {
        sockets.delete(socketId);

        if (sockets.size === 0) {
            activeUsers.delete(username);
        }
    }

    socketUsers.delete(socketId);

    console.log(`[offline] ${username} (${socketId})`);
}

function getTargetSocketIds(target) {
    if (typeof target !== "string" || !target) {
        return [];
    }

    if (socketUsers.has(target)) {
        return [target];
    }

    return Array.from(activeUsers.get(target) || []);
}

function emitToUser(username, event, data) {
    const socketIds = activeUsers.get(username);

    if (!socketIds || socketIds.size === 0) {
        return false;
    }

    for (const socketId of socketIds) {
        io.to(socketId).emit(event, data);
    }

    return true;
}

// 🔴 NEW: clear any call state involving this username.
function clearCallState(username) {
    if (!username) return null;

    const partner = activeCalls.get(username);
    if (partner !== undefined) {
        activeCalls.delete(username);
        activeCalls.delete(partner);
    }
    return partner;
}

io.on("connection", (socket) => {
    console.log(`[socket] Connected: ${socket.id}`);

    // ======================================
    // REGISTER USER
    // ======================================

    socket.on("register", async (rawUsername) => {
        try {
            if (typeof rawUsername !== "string") {
                socket.emit("registration-error", {
                    message: "Invalid username"
                });
                return;
            }

            const username = rawUsername.trim();

            if (!username || username.length > 50) {
                socket.emit("registration-error", {
                    message: "Invalid username"
                });
                return;
            }

            const result = await pool.query(
                "SELECT username FROM users WHERE username = $1",
                [username]
            );

            if (result.rows.length === 0) {
                socket.emit("registration-error", {
                    message: "User is not registered"
                });
                return;
            }

            removeOnlineUser(socket.id);

            socket.username = username;
            addOnlineUser(username, socket.id);

            socket.emit("registration-success", { username });

            console.log(`[online] ${username} (${socket.id})`);
        } catch (err) {
            console.error("Socket registration error:", err.message);

            socket.emit("registration-error", {
                message: "Registration failed"
            });
        }
    });

    // ======================================
    // REAL-TIME MESSAGE RELAY
    // ======================================

    socket.on("send-message", async (data) => {
        try {
            const senderUsername = socket.username;

            if (!senderUsername) {
                socket.emit("message-undelivered", {
                    clientMessageId: data?.clientMessageId || null,
                    reason: "NOT_REGISTERED"
                });
                return;
            }

            if (!data || typeof data !== "object") {
                return;
            }

            const clientMessageId = data.clientMessageId;
            const receiverUsername =
                typeof data.receiverUsername === "string"
                    ? data.receiverUsername.trim()
                    : "";

            const content = data.content;

            if (
                typeof clientMessageId !== "string" ||
                clientMessageId.length < 1 ||
                clientMessageId.length > 100 ||
                !receiverUsername ||
                receiverUsername.length > 50 ||
                typeof content !== "string" ||
                content.trim().length === 0 ||
                content.length > MAX_CONTENT
            ) {
                socket.emit("message-undelivered", {
                    clientMessageId: clientMessageId || null,
                    reason: "INVALID_MESSAGE"
                });
                return;
            }

            if (senderUsername === receiverUsername) {
                socket.emit("message-undelivered", {
                    clientMessageId,
                    reason: "CANNOT_MESSAGE_SELF"
                });
                return;
            }

            const receiverCheck = await pool.query(
                "SELECT username FROM users WHERE username = $1",
                [receiverUsername]
            );

            if (receiverCheck.rows.length === 0) {
                socket.emit("message-undelivered", {
                    clientMessageId,
                    reason: "USER_NOT_FOUND"
                });
                return;
            }

            const recipientSockets = activeUsers.get(receiverUsername);

            if (!recipientSockets || recipientSockets.size === 0) {
                socket.emit("message-undelivered", {
                    clientMessageId,
                    reason: "USER_OFFLINE"
                });

                console.log(
                    `[message-offline] ${senderUsername} -> ${receiverUsername}`
                );
                return;
            }

            const message = {
                clientMessageId,
                senderUsername,
                receiverUsername,
                content,
                timestamp: new Date().toISOString()
            };

            for (const recipientSocketId of recipientSockets) {
                io.to(recipientSocketId).emit("receive-message", message);
            }

            console.log(
                `[message-relayed] ${senderUsername} -> ${receiverUsername} (${clientMessageId})`
            );
        } catch (err) {
            console.error("Message relay error:", err.message);

            socket.emit("message-undelivered", {
                clientMessageId: data?.clientMessageId || null,
                reason: "SERVER_ERROR"
            });
        }
    });

    // ======================================
    // MESSAGE DELIVERY ACKNOWLEDGEMENT
    // ======================================

    socket.on("message-delivered", (data) => {
        try {
            const receiverUsername = socket.username;

            if (!receiverUsername || !data || typeof data !== "object") {
                return;
            }

            const clientMessageId = data.clientMessageId;
            const senderUsername = data.senderUsername;

            if (
                typeof clientMessageId !== "string" ||
                clientMessageId.length < 1 ||
                clientMessageId.length > 100 ||
                typeof senderUsername !== "string" ||
                !senderUsername.trim() ||
                senderUsername.trim() === receiverUsername
            ) {
                return;
            }

            const acknowledgement = {
                clientMessageId,
                senderUsername: senderUsername.trim(),
                receiverUsername
            };

            emitToUser(senderUsername.trim(), "message-delivered", acknowledgement);

            console.log(
                `[message-delivered] ${receiverUsername} -> ${senderUsername} (${clientMessageId})`
            );
        } catch (err) {
            console.error("Delivery acknowledgement error:", err.message);
        }
    });

    // ======================================
    // WEBRTC CALL SIGNALING
    // ======================================

    socket.on("call-user", ({ to, offer } = {}) => {
        if (!socket.username || !to || !offer) {
            socket.emit("call-failed", {
                message: "Invalid call request"
            });
            return;
        }

        // 🔴 NEW: caller already in a call.
        if (activeCalls.has(socket.username)) {
            socket.emit("call-failed", {
                message: "You are already in a call"
            });
            return;
        }

        // 🔴 NEW: target already in a call.
        if (activeCalls.has(to)) {
            socket.emit("call-failed", {
                message: "User is busy"
            });
            return;
        }

        const targetSocketIds = getTargetSocketIds(to);

        if (targetSocketIds.length === 0) {
            socket.emit("call-failed", {
                message: "User is offline"
            });
            return;
        }

        // 🔴 NEW: mark both sides as busy before emitting.
        activeCalls.set(socket.username, to);
        activeCalls.set(to, socket.username);

        for (const targetSocketId of targetSocketIds) {
            io.to(targetSocketId).emit("incoming-call", {
                from: socket.id,
                fromSocketId: socket.id,
                fromUsername: socket.username,
                offer
            });
        }

        console.log(`[call] ${socket.username} -> ${to}`);
    });

    socket.on("answer-call", ({ to, answer } = {}) => {
        if (!socket.username || !to || !answer) return;

        const targetSocketIds = getTargetSocketIds(to);

        for (const targetSocketId of targetSocketIds) {
            io.to(targetSocketId).emit("call-answered", {
                from: socket.id,
                fromUsername: socket.username,
                answer
            });
        }

        console.log(`[call-answered] ${socket.username} -> ${to}`);
    });

    socket.on("ice-candidate", ({ to, candidate } = {}) => {
        if (!socket.username || !to || !candidate) return;

        const targetSocketIds = getTargetSocketIds(to);

        for (const targetSocketId of targetSocketIds) {
            io.to(targetSocketId).emit("ice-candidate", {
                from: socket.id,
                fromUsername: socket.username,
                candidate
            });
        }
    });

    socket.on("end-call", ({ to } = {}) => {
        if (!socket.username) return;

        // 🔴 NEW: clean up call state.
        const partner = activeCalls.get(socket.username) || to;
        clearCallState(socket.username);
        if (partner) clearCallState(partner);

        const targetSocketIds = getTargetSocketIds(to || partner);

        for (const targetSocketId of targetSocketIds) {
            io.to(targetSocketId).emit("call-ended", {
                from: socket.id,
                fromUsername: socket.username
            });
        }

        console.log(`[call-ended] ${socket.username} -> ${to || partner}`);
    });

    // ======================================
    // DISCONNECT
    // ======================================

    socket.on("disconnect", () => {
        // 🔴 NEW: if this socket's user was in a call, notify the partner.
        const partner = clearCallState(socket.username);

        if (partner) {
            const targetSocketIds = getTargetSocketIds(partner);

            for (const targetSocketId of targetSocketIds) {
                io.to(targetSocketId).emit("call-ended", {
                    from: socket.id,
                    fromUsername: socket.username
                });
            }

            console.log(
                `[call-ended-disconnect] ${socket.username} -> ${partner}`
            );
        }

        removeOnlineUser(socket.id);
    });
});

// ==========================================
// 4. START SERVER
// ==========================================

async function startServer() {
    try {
        await setupDatabase();

        httpServer.listen(PORT, "0.0.0.0", () => {
            console.log(
                `PrivacyMessenger API + Socket.IO running on port ${PORT}`
            );
        });
    } catch (err) {
        console.error("Server startup failed:", err.message);
        await pool.end();
        process.exit(1);
    }
}

startServer();

// ==========================================
// 5. GRACEFUL SHUTDOWN
// ==========================================

let isShuttingDown = false;

async function shutdown() {
    if (isShuttingDown) return;

    isShuttingDown = true;
    console.log("Shutting down server...");

    io.close();

    try {
        await pool.end();
        console.log("Database connection closed.");
        process.exit(0);
    } catch (err) {
        console.error("Shutdown error:", err.message);
        process.exit(1);
    }
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
