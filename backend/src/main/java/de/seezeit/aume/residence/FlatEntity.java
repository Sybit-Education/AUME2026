package de.seezeit.aume.residence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "flats", uniqueConstraints = @UniqueConstraint(columnNames = {"residence_id", "label"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlatEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id", nullable = false)
    private ResidenceEntity residence;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false)
    private int capacity;

    public FlatEntity(ResidenceEntity residence, String label, int capacity) {
        this.residence = residence;
        this.label = label;
        this.capacity = capacity;
    }
}
