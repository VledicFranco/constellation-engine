# Complete Example

```
# Communication ranking pipeline
# Ranks communications for a specific user

type Communication = {
  communicationId: String,
  contentBlocks: List<String>,
  channel: String
}

type EmbeddingResult = {
  embedding: List<Float>
}

type ScoreResult = {
  score: Float,
  rank: Int
}

# Pipeline inputs
in communications: Candidates<Communication>
in mappedUserId: Int

# Step 1: Generate embeddings for each communication
embeddings = ide-ranker-v2-candidate-embed(communications)

# Step 2: Compute relevance scores using embeddings and user context
scores = ide-ranker-v2-score(embeddings + communications, mappedUserId)

# Step 3: Select output fields and merge with scores
result = communications[communicationId, channel] + scores[score, rank]

# Pipeline output
out result
```

Output type: `Candidates<{ communicationId: String, channel: String, score: Float, rank: Int }>`
