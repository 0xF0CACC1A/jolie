// Test: Multi-Level Path Traversal
// Expected: accounts[0] (.user.settings.theme==dark)

include "console.iol"

main {
    accounts[0].user.settings.theme = "dark";
    accounts[1].user.settings.theme = "light";
    accounts[2].user.settings.language = "en";

    paths "$[*]" into results from accounts where ".user.settings.theme == dark";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
