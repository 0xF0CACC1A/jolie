// Test field + array wildcard with string comparison

include "console.iol"

main {
    data.colors[0] = "red";
    data.colors[1] = "blue";
    data.colors[2] = "green";

    data.shapes[0] = "circle";
    data.shapes[1] = "square";

    data.sizes[0] = "small";
    data.sizes[1] = "large";

    // Find data where any field has any array element == "blue"
    res << paths data where $.*[*] == "blue";

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
