package dev.pocketbucket

// Fictional Data Center payloads. Additional real API fields deliberately exercise ignoreUnknownKeys.
internal object Fixtures {
    const val project = """{"key":"ORBIT","id":7,"name":"Orbital Tools","description":"Fictional spacecraft tooling","type":"NORMAL","public":false}"""
    val repository = """{"id":42,"slug":"flight-console","name":"Flight Console","scmId":"git","project":$project}"""
    const val person = """{"name":"alex.demo","displayName":"Alex Demo","id":10,"active":true,"type":"NORMAL"}"""
    val pullRequest = """{
        "id":12,"version":3,"title":"Add telemetry chart","description":"Fictional chart improvement.",
        "state":"OPEN","open":true,"closed":false,"createdDate":1700000000000,
        "author":{"user":$person,"role":"AUTHOR","approved":false,"status":"UNAPPROVED"},
        "fromRef":{"id":"refs/heads/feature/chart","displayId":"feature/chart","latestCommit":"abc123","repository":$repository},
        "toRef":{"id":"refs/heads/main","displayId":"main","latestCommit":"def456","repository":$repository},
        "reviewers":[{"user":{"name":"sam.demo","displayName":"Sam Demo"},"role":"REVIEWER","approved":true,"status":"APPROVED"}],
        "participants":[],"locked":false,"links":{"self":[{"href":"https://code.example.org/projects/ORBIT/repos/flight-console/pull-requests/12"}]}
    }"""
    fun page(values: String, last: Boolean = true, next: Int? = null) =
        """{"size":1,"limit":25,"start":0,"isLastPage":$last,${if (next != null) "\"nextPageStart\":$next," else ""}"values":[$values]}"""
}
