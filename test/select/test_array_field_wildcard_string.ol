// Test [*].* with string filtering

include "console.iol"

main {
    users[0].name = "Alice";
    users[0].role = "admin";
    users[1].name = "Bob";
    users[1].role = "user";
    users[2].name = "Charlie";
    users[2].role = "admin";

    // Get only admin roles
    res << paths users[*].* where $ == "admin";

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
