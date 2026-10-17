package vn.edu.ptit.english.dto;

import java.util.List;

public record UserPage(List<UserView> items, long totalElements, int page, int size, int totalPages) {}
