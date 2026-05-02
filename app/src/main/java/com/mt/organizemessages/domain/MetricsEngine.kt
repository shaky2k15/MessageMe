package com.mt.organizemessages.domain

import com.mt.organizemessages.ChatMessage

class MetricsEngine {

    fun generateMetricsHtml(messages: List<ChatMessage>): String {
        val totalMessages = messages.size
        val tagCounts = mutableMapOf<String, Int>()
        val senderCounts = mutableMapOf<String, Int>()

        messages.forEach { msg ->
            msg.tags.forEach { tag ->
                tagCounts[tag] = tagCounts.getOrDefault(tag, 0) + 1
            }
            senderCounts[msg.address] = senderCounts.getOrDefault(msg.address, 0) + 1
        }

        val sortedTags = tagCounts.toList().sortedByDescending { it.second }
        val frequentSender = senderCounts.toList().maxByOrNull { it.second }?.first ?: "N/A"
        val totalTags = tagCounts.size

        return """
            <html>
            <head>
                <style>
                    body { font-family: sans-serif; padding: 20px; background-color: #f8f9fa; color: #333; }
                    .card { background: white; padding: 15px; border-radius: 10px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); margin-bottom: 20px; }
                    h1 { color: #6200EE; }
                    .stat { font-size: 1.2em; margin: 10px 0; }
                    .tag-list { list-style: none; padding: 0; }
                    .tag-item { display: flex; justify-content: space-between; padding: 5px 0; border-bottom: 1px solid #eee; }
                </style>
            </head>
            <body>
                <h1>MessageMe Metrics</h1>
                <div class="card">
                    <div class="stat"><b>Total Messages:</b> $totalMessages</div>
                    <div class="stat"><b>Unique Tags:</b> $totalTags</div>
                    <div class="stat"><b>Frequent Sender:</b> $frequentSender</div>
                </div>
                <div class="card">
                    <h3>Messages per Tag</h3>
                    <ul class="tag-list">
                        ${sortedTags.joinToString("") { "<li class='tag-item'><span>${it.first}</span> <span>${it.second}</span></li>" }}
                    </ul>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
