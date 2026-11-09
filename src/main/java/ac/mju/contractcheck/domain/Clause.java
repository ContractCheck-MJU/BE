package ac.mju.contractcheck.domain;

public record Clause(long id, long contractId, String owner, int no, String title, String text, String riskLevel) {
    public Clause withEdit(Integer no, String title, String text) {
        return new Clause(
                id,
                contractId,
                owner,
                no == null ? this.no : no,
                title == null ? this.title : title,
                text == null ? this.text : text,
                riskLevel);
    }
}
