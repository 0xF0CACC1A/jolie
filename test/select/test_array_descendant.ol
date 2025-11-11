// Test: Array iteration with descendant search in WHERE
// Expected: companies[0].projects[0] (status==in_progress AND technologies contains Python)

include "console.iol"

main {
    companies[0].projects[0].project_id = "P001";
    companies[0].projects[0].status = "in_progress";
    companies[0].projects[0].technologies[0] = "Python";
    companies[0].projects[0].technologies[1] = "Django";

    companies[0].projects[1].project_id = "P002";
    companies[0].projects[1].status = "completed";
    companies[0].projects[1].technologies[0] = "Python";

    companies[0].projects[2].project_id = "P003";
    companies[0].projects[2].status = "in_progress";
    companies[0].projects[2].technologies[0] = "Java";

    select "$[*].projects[*]" into results from companies
    where ".status == in_progress && ..technologies[*] == Python";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
