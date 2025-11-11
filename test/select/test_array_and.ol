// Test: Array iteration with AND operator
// Expected: items[0], items[3] (type==user AND status==active)

include "console.iol"

main {
    items[0].type = "user";
    items[0].status = "active";

    items[1].type = "admin";
    items[1].status = "active";

    items[2].type = "user";
    items[2].status = "inactive";

    items[3].type = "user";
    items[3].status = "active";

    select "$[*]" into results from items where ".type == user && .status == active";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
