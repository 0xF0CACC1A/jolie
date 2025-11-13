// Test: Complex nested structure with descendant search
// Expected: companies[0].departments[0].teams[0].projects[0], companies[2].departments[0].teams[0].projects[1]

include "console.iol"

main {
    // Company 0 - has in_progress project with Python
    companies[0].name = "TechCorp";
    companies[0].departments[0].teams[0].projects[0].project_id = "P001";
    companies[0].departments[0].teams[0].projects[0].status = "in_progress";
    companies[0].departments[0].teams[0].projects[0].technologies[0] = "Python";
    companies[0].departments[0].teams[0].projects[0].technologies[1] = "Django";

    // Company 1 - has completed project with Python
    companies[1].name = "DataSystems";
    companies[1].departments[0].teams[0].projects[0].project_id = "P100";
    companies[1].departments[0].teams[0].projects[0].status = "completed";
    companies[1].departments[0].teams[0].projects[0].technologies[0] = "Python";

    // Company 2 - has multiple projects, one matches criteria
    companies[2].name = "CloudServices";
    companies[2].departments[0].teams[0].projects[0].project_id = "P200";
    companies[2].departments[0].teams[0].projects[0].status = "in_progress";
    companies[2].departments[0].teams[0].projects[0].technologies[0] = "Java";

    companies[2].departments[0].teams[0].projects[1].project_id = "P201";
    companies[2].departments[0].teams[0].projects[1].status = "in_progress";
    companies[2].departments[0].teams[0].projects[1].technologies[0] = "Python";
    companies[2].departments[0].teams[0].projects[1].technologies[1] = "Flask";

    // Query: in_progress projects with Python
    paths "$[*]..projects[*]" into results from companies
    where ".status == in_progress && ..technologies[*] == Python";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
