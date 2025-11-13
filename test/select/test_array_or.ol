// Test: Array iteration with OR operator
// Expected: items[0], items[3] (status==active OR status==completed)

include "console.iol"

main {
    items[0].status = "active";
    items[1].status = "pending";
    items[2].status = "inactive";
    items[3].status = "completed";

    paths "$[*]" into results from items where ".status == active || .status == completed";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
