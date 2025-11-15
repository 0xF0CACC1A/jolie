// Test with negation operator in WHERE clause

include "console.iol"

main {
    values.x[0] = 5;
    values.x[1] = 15;
    values.x[2] = 25;
    values.y[0] = 10;
    values.y[1] = 20;
    values.y[2] = 30;

    // NOT operator: get all values NOT between 10 and 20
    res << paths values.*[*] where !($ >= 10 && $ <= 20);

    println@Console("Values outside [10,20]:")();
    for (i = 0, i < #res.results, i++) {
        println@Console("  " + res.results[i])()
    }
}
