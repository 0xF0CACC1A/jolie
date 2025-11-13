// Test array wildcard on deeply nested field: data.users.list[*]

include "console.iol"

main {
    data.users.list[0] = 10;
    data.users.list[1] = 20;
    data.users.list[2] = 30;
    data.other = "value";

    res << paths data.users.list[*] where $ > 15;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
