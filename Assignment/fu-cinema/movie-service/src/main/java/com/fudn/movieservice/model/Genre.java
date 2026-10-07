package com.fudn.movieservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "genres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Genre {

    @Id                                  // map sang _id; null khi tao moi -> MongoDB sinh ObjectId
    private String genreId;

    @Indexed(unique = true)
    private String genreName;

    private String description;
}
