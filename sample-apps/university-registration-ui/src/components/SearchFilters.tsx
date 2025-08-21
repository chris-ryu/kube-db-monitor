'use client';

import { useState } from 'react';
import { Search, Filter } from 'lucide-react';

interface SearchFiltersProps {
  onSearch: (filters: SearchFilters) => void;
  loading?: boolean;
}

interface SearchFilters {
  keyword: string;
  department: string;
  credits: string;
  timeSlot: string;
  availableOnly: boolean;
}

const DEPARTMENTS = [
  { id: '', name: '전체 학과' },
  { id: '1', name: '컴퓨터공학과' },
  { id: '2', name: '전자공학과' },
  { id: '3', name: '경영학과' },
  { id: '4', name: '수학과' },
  { id: '5', name: '물리학과' },
];

const CREDITS = [
  { value: '', label: '전체' },
  { value: '1', label: '1학점' },
  { value: '2', label: '2학점' },
  { value: '3', label: '3학점' },
  { value: '4', label: '4학점' },
];

const TIME_SLOTS = [
  { value: '', label: '전체 시간' },
  { value: 'morning', label: '오전 (9-12시)' },
  { value: 'afternoon', label: '오후 (12-18시)' },
  { value: 'evening', label: '저녁 (18시 이후)' },
];

export default function SearchFilters({ onSearch, loading = false }: SearchFiltersProps) {
  const [filters, setFilters] = useState<SearchFilters>({
    keyword: '',
    department: '',
    credits: '',
    timeSlot: '',
    availableOnly: false,
  });

  const [showAdvanced, setShowAdvanced] = useState(false);

  const handleFilterChange = (key: keyof SearchFilters, value: string | boolean) => {
    const newFilters = { ...filters, [key]: value };
    setFilters(newFilters);
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    onSearch(filters);
  };

  const handleReset = () => {
    const resetFilters = {
      keyword: '',
      department: '',
      credits: '',
      timeSlot: '',
      availableOnly: false,
    };
    setFilters(resetFilters);
    onSearch(resetFilters);
  };

  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
      <form onSubmit={handleSearch} className="space-y-4">
        {/* 기본 검색 */}
        <div className="flex gap-4">
          <div className="flex-1">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" size={20} />
              <input
                type="text"
                placeholder="과목명, 교수명 검색..."
                value={filters.keyword}
                onChange={(e) => handleFilterChange('keyword', e.target.value)}
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-gray-900 placeholder-gray-500"
              />
            </div>
          </div>
          <button
            type="submit"
            disabled={loading}
            className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? '검색중...' : '검색'}
          </button>
          <button
            type="button"
            onClick={() => setShowAdvanced(!showAdvanced)}
            className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 flex items-center gap-2"
          >
            <Filter size={20} />
            상세검색
          </button>
        </div>

        {/* 상세 필터 */}
        {showAdvanced && (
          <div className="pt-4 border-t border-gray-200 space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {/* 학과 선택 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  학과
                </label>
                <select
                  value={filters.department}
                  onChange={(e) => handleFilterChange('department', e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-gray-900"
                >
                  {DEPARTMENTS.map((dept) => (
                    <option key={dept.id} value={dept.id}>
                      {dept.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* 학점 선택 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  학점
                </label>
                <select
                  value={filters.credits}
                  onChange={(e) => handleFilterChange('credits', e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-gray-900"
                >
                  {CREDITS.map((credit) => (
                    <option key={credit.value} value={credit.value}>
                      {credit.label}
                    </option>
                  ))}
                </select>
              </div>

              {/* 시간대 선택 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  시간대
                </label>
                <select
                  value={filters.timeSlot}
                  onChange={(e) => handleFilterChange('timeSlot', e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-gray-900"
                >
                  {TIME_SLOTS.map((slot) => (
                    <option key={slot.value} value={slot.value}>
                      {slot.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* 추가 옵션 */}
            <div className="flex items-center justify-between">
              <label className="flex items-center">
                <input
                  type="checkbox"
                  checked={filters.availableOnly}
                  onChange={(e) => handleFilterChange('availableOnly', e.target.checked)}
                  className="w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 rounded focus:ring-blue-500"
                />
                <span className="ml-2 text-sm text-gray-700">
                  신청 가능한 과목만 보기
                </span>
              </label>

              <button
                type="button"
                onClick={handleReset}
                className="text-sm text-gray-500 hover:text-gray-700"
              >
                필터 초기화
              </button>
            </div>
          </div>
        )}
      </form>
    </div>
  );
}