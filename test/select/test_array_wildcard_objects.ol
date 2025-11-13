// Test array wildcard with objects using $.field

include "console.iol"

main {
    users[0].name = "Alice";
    users[0].age = 25;
    users[1].name = "Bob";
    users[1].age = 30;
    users[2].name = "Charlie";
    users[2].age = 20;

    res << paths users[*] where $.age > 22;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
