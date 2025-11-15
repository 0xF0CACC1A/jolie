// Test combining wildcard array with complex field access in WHERE

include "console.iol"

main {
    inventory.warehouse1[0].item.code = "A123";
    inventory.warehouse1[0].item.quantity = 50;
    inventory.warehouse1[0].item.location.shelf = 5;

    inventory.warehouse1[1].item.code = "B456";
    inventory.warehouse1[1].item.quantity = 150;
    inventory.warehouse1[1].item.location.shelf = 3;

    inventory.warehouse2[0].item.code = "C789";
    inventory.warehouse2[0].item.quantity = 75;
    inventory.warehouse2[0].item.location.shelf = 8;

    inventory.warehouse2[1].item.code = "D012";
    inventory.warehouse2[1].item.quantity = 200;
    inventory.warehouse2[1].item.location.shelf = 2;

    // Find items with quantity > 100
    res << paths inventory.*[*] where $.item.quantity > 100;

    println@Console("High quantity items:")();
    for (i = 0, i < #res.results, i++) {
        println@Console("  " + res.results[i])()
    }
}
