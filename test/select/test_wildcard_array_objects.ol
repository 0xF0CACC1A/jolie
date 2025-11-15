// Test wildcard array with objects and $.field access

include "console.iol"

main {
    items.users[0].name = "Alice";
    items.users[0].age = 25;
    items.users[1].name = "Bob";
    items.users[1].age = 30;
    items.products[0].name = "Widget";
    items.products[0].price = 15;
    items.products[1].name = "Gadget";
    items.products[1].price = 28;

    res << paths items.*[*] where $.age > 20 || $.price > 20;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
