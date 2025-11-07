// Test: Field existence check with AND
// Expected: items[0] (has email AND age==30)

main {
    items[0].name = "Alice";
    items[0].age = 30;
    items[0].email = "alice@example.com";

    items[1].name = "Bob";
    items[1].age = 25;
    items[1].email = "bob@example.com";

    items[2].name = "Charlie";
    items[2].age = 30;

    items[3].name = "David";
    items[3].email = "david@example.com";

    select "$[*]" into results from items where ".email in . && .age == 30";

    i = 0;
    while( i < #results ) {
        print results[i];
        i++
    }
}
