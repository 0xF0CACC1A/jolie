// Test: Nested Path Existence
// Expected: users[0] (has .settings.notifications)

include "console.iol"

main {
    users[0].name = "Alice";
    users[0].settings.notifications.enabled = "true";

    users[1].name = "Bob";
    users[1].settings.theme = "dark";

    users[2].name = "Charlie";

    select "$[*]" into results from users where ".settings.notifications in .";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
