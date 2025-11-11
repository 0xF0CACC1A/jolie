// Test: Array iteration with NOT operator
// Expected: items[1] (status==active AND NOT type==admin)

include "console.iol"

main {
    items[0].type = "admin";
    items[0].status = "active";

    items[1].type = "user";
    items[1].status = "active";

    items[2].type = "admin";
    items[2].status = "inactive";

    select "$[*]" into results from items where ".status == active && !.type == admin";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
