package com.irene.twelvebooks.book;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

	Optional<Book> findByIsbn13(String isbn13);

	Optional<Book> findBySourceKey(String sourceKey);
}
