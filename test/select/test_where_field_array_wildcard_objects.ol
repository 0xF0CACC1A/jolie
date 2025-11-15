// Test field + array wildcard with object array elements

include "console.iol"

main {
    data.users[0].age = 25;
    data.users[0].name = "Alice";

    data.users[1].age = 35;
    data.users[1].name = "Bob";

    data.products[0].price = 50;
    data.products[1].price = 150;

    // Find data where any field has any array element with age > 30
    res << paths data where $.*[*].age > 30;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
