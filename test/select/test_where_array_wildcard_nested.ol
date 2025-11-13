// Test nested array wildcard in WHERE clause: $.addresses[*].city == "NYC"

include "console.iol"

main {
    data.users[0].name = "Alice";
    data.users[0].addresses[0].city = "NYC";
    data.users[0].addresses[1].city = "LA";

    data.users[1].name = "Bob";
    data.users[1].addresses[0].city = "Boston";
    data.users[1].addresses[1].city = "Chicago";

    data.users[2].name = "Charlie";
    data.users[2].addresses[0].city = "SF";
    data.users[2].addresses[1].city = "NYC";

    res << paths data.users[*] where $.addresses[*].city == "NYC";

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
