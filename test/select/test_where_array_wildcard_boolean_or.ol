// Test array wildcard combined with OR: $.tags[*] == "urgent" || $.category == "high"
// Should match if ANY tag is "urgent" OR category is "high"

include "console.iol"

main {
    data.tasks[0].tags[0] = "urgent";
    data.tasks[0].tags[1] = "important";
    data.tasks[0].category = "low";

    data.tasks[1].tags[0] = "normal";
    data.tasks[1].tags[1] = "routine";
    data.tasks[1].category = "high";

    data.tasks[2].tags[0] = "postponed";
    data.tasks[2].tags[1] = "review";
    data.tasks[2].category = "medium";

    data.tasks[3].tags[0] = "urgent";
    data.tasks[3].tags[1] = "critical";
    data.tasks[3].category = "high";

    res << paths data.tasks[*] where $.tags[*] == "urgent" || $.category == "high";

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
