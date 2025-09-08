import React, { useState } from "react";
import { User } from "@/api/entities";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { User as UserIcon, Mail, Calendar, Save, CheckCircle } from "lucide-react";
import { format } from "date-fns";

export default function AccountInfo({ user, onUserUpdate }) {
  const [fullName, setFullName] = useState(user?.full_name || "");
  const [isUpdating, setIsUpdating] = useState(false);
  const [updateSuccess, setUpdateSuccess] = useState(false);

  const handleSaveChanges = async () => {
    setIsUpdating(true);
    try {
      const updatedUser = await User.updateMyUserData({ 
        full_name: fullName 
      });
      onUserUpdate(updatedUser);
      setUpdateSuccess(true);
      setTimeout(() => setUpdateSuccess(false), 3000);
    } catch (error) {
      console.error("계정 정보 업데이트 실패:", error);
    }
    setIsUpdating(false);
  };

  return (
    <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-blue-500/10 to-purple-500/10 border border-blue-500/20">
      <div className="flex items-center gap-3 mb-6">
        <div className="p-2 rounded-lg bg-gradient-to-r from-blue-500/20 to-purple-500/20">
          <UserIcon className="w-5 h-5 text-blue-400" />
        </div>
        <h2 className="text-2xl font-bold text-white">계정 정보</h2>
        {updateSuccess && (
          <Badge className="bg-green-500/20 text-green-400 border-green-500/30">
            <CheckCircle className="w-3 h-3 mr-1" />
            저장됨
          </Badge>
        )}
      </div>

      <div className="space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="text-white/80 text-sm font-medium mb-2 block">
              이름
            </label>
            <Input
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              className="glass-morphism border-white/20 text-white"
              placeholder="전체 이름을 입력하세요"
            />
          </div>

          <div>
            <label className="text-white/80 text-sm font-medium mb-2 block">
              이메일 (변경 불가)
            </label>
            <Input
              value={user?.email || ""}
              disabled
              className="glass-morphism border-white/10 text-white/60 bg-white/5"
            />
          </div>

          <div>
            <label className="text-white/80 text-sm font-medium mb-2 block">
              권한 레벨
            </label>
            <Badge className={`${
              user?.role === 'admin' 
                ? 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30' 
                : 'bg-blue-500/20 text-blue-400 border-blue-500/30'
            } p-2`}>
              {user?.role === 'admin' ? '관리자' : '사용자'}
            </Badge>
          </div>

          <div>
            <label className="text-white/80 text-sm font-medium mb-2 block">
              계정 생성일
            </label>
            <div className="flex items-center gap-2 text-white/60">
              <Calendar className="w-4 h-4" />
              <span>
                {user?.created_date ? format(new Date(user.created_date), "yyyy년 MM월 dd일") : "-"}
              </span>
            </div>
          </div>
        </div>

        <div className="pt-4 border-t border-white/10">
          <h3 className="text-lg font-semibold text-white mb-4">로그인 정보</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <p className="text-white/60 text-sm">마지막 로그인</p>
              <p className="text-white/80">
                {user?.last_login ? format(new Date(user.last_login), "yyyy-MM-dd HH:mm") : "정보 없음"}
              </p>
            </div>
            <div>
              <p className="text-white/60 text-sm">계정 상태</p>
              <Badge className={`${
                user?.account_locked 
                  ? 'bg-red-500/20 text-red-400 border-red-500/30'
                  : 'bg-green-500/20 text-green-400 border-green-500/30'
              }`}>
                {user?.account_locked ? '잠김' : '활성'}
              </Badge>
            </div>
          </div>
        </div>

        <div className="flex justify-end">
          <Button
            onClick={handleSaveChanges}
            disabled={isUpdating || fullName === user?.full_name}
            className="bg-blue-500/20 text-blue-400 border border-blue-500/30 hover:bg-blue-500/30"
          >
            <Save className="w-4 h-4 mr-2" />
            {isUpdating ? "저장 중..." : "변경사항 저장"}
          </Button>
        </div>
      </div>
    </div>
  );
}