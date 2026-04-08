// ============================================================
// AURALINK - MongoDB Schema (Chat Service)
// ============================================================

const db = connect("mongodb://localhost:27017/dating_chat");

// ─── Conversations Collection ────────────────────────────────
db.createCollection("conversations", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["_id", "user1Id", "user2Id"],
            properties: {
                _id:           { bsonType: "string", description: "matchId" },
                user1Id:       { bsonType: "string" },
                user2Id:       { bsonType: "string" },
                lastMessage:   { bsonType: "string" },
                lastMessageAt: { bsonType: "date" },
                createdAt:     { bsonType: "date" }
            }
        }
    }
});

db.conversations.createIndex({ user1Id: 1 });
db.conversations.createIndex({ user2Id: 1 });
db.conversations.createIndex({ lastMessageAt: -1 });
db.conversations.createIndex(
    { user1Id: 1, user2Id: 1 },
    { unique: true }
);

// ─── Messages Collection ─────────────────────────────────────
db.createCollection("messages", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["conversationId", "senderId", "type", "sentAt"],
            properties: {
                conversationId: { bsonType: "string" },
                senderId:       { bsonType: "string" },
                type:           {
                    bsonType: "string",
                    enum: ["TEXT", "IMAGE", "VOICE", "VIDEO", "LOCATION", "GIF"]
                },
                text:        { bsonType: "string" },
                mediaUrl:    { bsonType: "string" },
                locationLat: { bsonType: "double" },
                locationLong:{ bsonType: "double" },
                seen:        { bsonType: "bool" },
                seenAt:      { bsonType: "date" },
                sentAt:      { bsonType: "date" }
            }
        }
    }
});

db.messages.createIndex({ conversationId: 1, sentAt: -1 });
db.messages.createIndex({ senderId: 1 });
db.messages.createIndex({ seen: 1 });

// TTL: auto-delete messages older than 1 year (optional)
db.messages.createIndex(
    { sentAt: 1 },
    { expireAfterSeconds: 31536000 }
);

// ─── Sample data ─────────────────────────────────────────────
db.conversations.insertOne({
    _id: "match-sample-001",
    user1Id: "user-001",
    user2Id: "user-002",
    lastMessage: "Hey! 👋",
    lastMessageAt: new Date(),
    createdAt: new Date()
});

db.messages.insertOne({
    conversationId: "match-sample-001",
    senderId: "user-001",
    type: "TEXT",
    text: "Hey! 👋",
    seen: false,
    sentAt: new Date()
});

print("MongoDB schema created successfully!");