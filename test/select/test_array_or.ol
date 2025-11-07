// Test: Array iteration with OR operator
// Expected: items[0], items[3] (status==active OR status==completed)

main {
    items[0].status = "active";
    items[1].status = "pending";
    items[2].status = "inactive";
    items[3].status = "completed";

    select "$[*]" into results from items where ".status == active || .status == completed";

    i = 0;
    while( i < #results ) {
        print results[i];
        i++
    }
}
