package com.procurement.engine.constraint.entity;

import com.procurement.engine.procurement.entity.ProcurementRequest;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "procurement_constraints")
public class ProcurementConstraint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_id", nullable = false)
    private ProcurementRequest procurement;

    @Column(nullable = false, length = 100)
    private String attribute;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 30)
    private ConstraintOperator operator;

    @Column(name = "constraint_value", nullable = false, length = 500)
    private String value;

    @Column(length = 50)
    private String unit;

    @Column(nullable = false)
    private boolean mandatory = true;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal weight = BigDecimal.ONE;

    public ProcurementConstraint() {}

    public ProcurementConstraint(UUID id, ProcurementRequest procurement, String attribute, ConstraintOperator operator, String value, String unit, boolean mandatory, BigDecimal weight) {
        this.id = id;
        this.procurement = procurement;
        this.attribute = attribute;
        this.operator = operator;
        this.value = value;
        this.unit = unit;
        this.mandatory = mandatory;
        this.weight = weight != null ? weight : BigDecimal.ONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private ProcurementRequest procurement;
        private String attribute;
        private ConstraintOperator operator;
        private String value;
        private String unit;
        private boolean mandatory = true;
        private BigDecimal weight = BigDecimal.ONE;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder procurement(ProcurementRequest procurement) { this.procurement = procurement; return this; }
        public Builder attribute(String attribute) { this.attribute = attribute; return this; }
        public Builder operator(ConstraintOperator operator) { this.operator = operator; return this; }
        public Builder value(String value) { this.value = value; return this; }
        public Builder unit(String unit) { this.unit = unit; return this; }
        public Builder mandatory(boolean mandatory) { this.mandatory = mandatory; return this; }
        public Builder weight(BigDecimal weight) { this.weight = weight; return this; }

        public ProcurementConstraint build() {
            return new ProcurementConstraint(id, procurement, attribute, operator, value, unit, mandatory, weight);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProcurementRequest getProcurement() { return procurement; }
    public void setProcurement(ProcurementRequest procurement) { this.procurement = procurement; }
    public String getAttribute() { return attribute; }
    public void setAttribute(String attribute) { this.attribute = attribute; }
    public ConstraintOperator getOperator() { return operator; }
    public void setOperator(ConstraintOperator operator) { this.operator = operator; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
}
