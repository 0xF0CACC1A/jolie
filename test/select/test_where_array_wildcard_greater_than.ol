// Test array wildcard with greater than: $.scores[*] > 80
// Should match if ANY score > 80

include "console.iol"

main {
    data.students[0].name = "Alice";
    data.students[0].scores[0] = 70;
    data.students[0].scores[1] = 90;

    data.students[1].name = "Bob";
    data.students[1].scores[0] = 60;
    data.students[1].scores[1] = 75;

    data.students[2].name = "Charlie";
    data.students[2].scores[0] = 85;
    data.students[2].scores[1] = 95;

    res << paths data.students[*] where $.scores[*] > 80;

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
