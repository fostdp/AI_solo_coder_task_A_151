package com.waterwheel.chaintransmission.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "chain_link_params")
public class ChainLinkParams {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "param_id")
    private Long paramId;

    @Column(name = "device_id", nullable = false)
    private Integer deviceId;

    @Column(name = "link_mass", precision = 10, scale = 6)
    private BigDecimal linkMass;

    @Column(name = "link_length", precision = 10, scale = 4)
    private BigDecimal linkLength;

    @Column(name = "link_stiffness", precision = 14, scale = 4)
    private BigDecimal linkStiffness;

    @Column(name = "link_damping", precision = 10, scale = 6)
    private BigDecimal linkDamping;

    @Column(name = "friction_coefficient", precision = 8, scale = 6)
    private BigDecimal frictionCoefficient;

    @Column(name = "allowable_tension", precision = 12, scale = 4)
    private BigDecimal allowableTension;

    @Column(name = "material", length = 50)
    private String material;
}
